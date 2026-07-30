package com.fohanalyzer;

/**
 * The application version, read once from the jar manifest.
 *
 * <p>
 * {@code maven-jar-plugin} writes {@code Implementation-Version} from the pom
 * version, so the number on screen cannot drift from the one the build stamps
 * on the bundle. A run straight from {@code target/classes} — {@code
 * mvn javafx:run} — has no manifest to read, and reports {@link #DEV} instead.
 */
public final class Version
{
	/** Shown when there is no manifest, i.e. an unpackaged development run. */
	public static final String DEV = "dev";

	public static final String VALUE = read();

	private Version()
	{
	}

	private static String read()
	{
		String v = Version.class.getPackage().getImplementationVersion();
		return v == null || v.isBlank() ? DEV : v;
	}
}
