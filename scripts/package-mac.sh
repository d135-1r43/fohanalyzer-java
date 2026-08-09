#!/usr/bin/env bash
#
# Build a self-contained FOHanalyzer bundle with jpackage — no JDK or Maven needed on the
# machine that runs it. Produces target/dist/FOHanalyzer-<version>.dmg by default; pass
# "app-image" to stop at target/dist/FOHanalyzer.app.
#
#   ./scripts/package-mac.sh            # .dmg
#   ./scripts/package-mac.sh app-image  # .app only, faster for testing
#
set -euo pipefail

cd "$(dirname "$0")/.."

APP_NAME="FOHanalyzer"
MAIN_CLASS="com.fohanalyzer.Main"
BUNDLE_ID="net.herhoffer.fohanalyzer"
TYPE="${1:-dmg}"
DIST="target/dist"
INPUT="target/jpackage-input"
APP="$DIST/$APP_NAME.app"

# The app is not modular (JavaFX runs from the classpath via the Main launcher), so jpackage
# cannot infer the runtime's contents. java.desktop carries javax.sound.sampled — without it
# there is no audio capture at all — and java.prefs backs the saved settings.
JDK_MODULES="java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported"

# Both steps below need a JDK at least as new as maven.compiler.release in pom.xml, and
# neither says so clearly when it is missing. jpackage stamps whichever runtime it is run
# from into the bundle, so an older one yields an .app whose JVM cannot load the very classes
# it ships. The icon render is worse: `mvn javafx:run` reads the JavaFX jars in-process, in
# the JVM running Maven, and an older JVM cannot parse their class files at all — it drops
# them and reports only "--add-modules requires modules to be specified". The pom's toolchain
# does not cover either case; it feeds the compiler and the test JVM, not the Maven JVM.
REQUIRED_JDK=25

# Leading integer of a version banner: "openjdk 25.0.3 ..." and "25.0.3" both give 25.
jdk_major()
{
	sed -n 's/^[^0-9]*\([0-9][0-9]*\).*/\1/p' <<<"$1" | head -1
}

require_jdk()
{
	local what="$1" reported="$2" major
	major="$(jdk_major "$reported")"
	if [ -z "$major" ] || [ "$major" -lt "$REQUIRED_JDK" ]; then
		echo "error: $what reports '${reported:-nothing}', but JDK $REQUIRED_JDK or newer is required." >&2
		echo "       export JAVA_HOME=\$(/usr/libexec/java_home -v $REQUIRED_JDK)" >&2
		exit 1
	fi
}

command -v jpackage >/dev/null 2>&1 || {
	echo "error: jpackage is not on PATH; it ships with the JDK." >&2
	exit 1
}
require_jdk "jpackage" "$(jpackage --version 2>&1 | head -1)"
# Ask Maven which JVM it runs on rather than checking `java`: Maven prefers JAVA_HOME, so the
# two can disagree, and it is Maven's JVM that renders the icon.
require_jdk "the JVM running Maven" "$(mvn -v 2>/dev/null | sed -n 's/^Java version: \([^,]*\).*/\1/p')"

VERSION="$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR="fohanalyzer-java-${VERSION}.jar"

# jpackage takes a numeric X[.Y[.Z]] app version and rejects anything else, so a snapshot
# build has to hand it the release number. The .dmg is renamed back to the full version
# below, so what lands in target/dist still says which build it is.
APP_VERSION="${VERSION%-SNAPSHOT}"

echo "==> building $APP_NAME $VERSION"
mvn -B -q clean package

echo "==> collecting runtime jars"
rm -rf "$INPUT" "$DIST"
mkdir -p "$INPUT"
# Includes the platform-classifier JavaFX jars, which carry the native libraries.
mvn -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$PWD/$INPUT"
cp "target/$JAR" "$INPUT/"

echo "==> rendering icon from the in-app logo"
mvn -B -q javafx:run -Dapp.mainClass=com.fohanalyzer.dev.IconRenderer
iconutil -c icns "target/$APP_NAME.iconset" -o "target/$APP_NAME.icns"

echo "==> jpackage app-image"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "Markus Herhoffer" \
  --description "Real-time dual-channel RTA for Front-of-House engineers" \
  --copyright "GPL-3.0-or-later" \
  --icon "target/$APP_NAME.icns" \
  --input "$INPUT" \
  --main-jar "$JAR" \
  --main-class "$MAIN_CLASS" \
  --dest "$DIST" \
  --add-modules "$JDK_MODULES" \
  --mac-package-identifier "$BUNDLE_ID"

# macOS 10.14+ refuses microphone access to an app whose Info.plist does not say why it
# wants it — for an RTA that means no signal at all. jpackage has no flag for arbitrary
# Info.plist keys, so patch the generated plist directly.
#
# jpackage on JDK 25 already writes a generic "is requesting access to the microphone";
# replace it with the actual reason, since this is the prompt the user has to agree to.
# Older JDKs write no such key, hence the Add fallback.
echo "==> declaring microphone usage"
PLIST="$APP/Contents/Info.plist"
MIC_REASON="FOHanalyzer reads your measurement mic and console outputs to display the live spectrum and measure SPL."
/usr/libexec/PlistBuddy -c "Set :NSMicrophoneUsageDescription $MIC_REASON" "$PLIST" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Add :NSMicrophoneUsageDescription string $MIC_REASON" "$PLIST"

# Editing the plist invalidates the ad-hoc signature jpackage applied, and arm64 macOS will
# not launch an unsigned bundle. Re-sign ad-hoc (a real Developer ID + notarisation is a
# separate concern, tracked apart from this script).
echo "==> re-signing ad-hoc"
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP" && echo "    signature ok"

if [ "$TYPE" != "app-image" ]; then
  echo "==> jpackage dmg"
  jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --app-image "$APP" \
    --dest "$DIST"

  if [ "$VERSION" != "$APP_VERSION" ]; then
    mv "$DIST/$APP_NAME-$APP_VERSION.dmg" "$DIST/$APP_NAME-$VERSION.dmg"
  fi
fi

echo "==> done"
du -sh "$APP"
ls -la "$DIST"
