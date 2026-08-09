## Install

Drag FOHanalyzer to Applications. It ships its own Java runtime, so **no JDK is needed**.

macOS asks for microphone permission the first time you select a live input — that covers
the measurement mic and console feeds, and without it the analyser has nothing to read.

The bundle is ad-hoc signed and not notarised, so Gatekeeper refuses the first launch.
Either clear the quarantine flag:

    xattr -dr com.apple.quarantine /Applications/FOHanalyzer.app

or open it once and press **Open Anyway** under **System Settings → Privacy & Security**.
On macOS 15 and later those are the only two routes — the older right-click-Open shortcut no
longer exists.

**Apple silicon only**, macOS 13 or later.
