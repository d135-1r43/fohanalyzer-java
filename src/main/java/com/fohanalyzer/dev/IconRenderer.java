package com.fohanalyzer.dev;

import com.fohanalyzer.ui.controls.Logo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build tool: renders the app icon from the in-app {@link Logo} artwork, so the
 * icon cannot drift from the logo in the header. Writes a macOS
 * {@code .iconset} directory that {@code iconutil} turns into an {@code .icns}.
 *
 * <p>
 * Each size is rendered from the vector artwork rather than downscaled from one
 * large bitmap — the bars are thin, and resampling them turns the small sizes
 * to mush.
 *
 * <p>
 * Run via {@code scripts/package-mac.sh}, or directly:
 * {@code mvn javafx:run -Dapp.mainClass=com.fohanalyzer.dev.IconRenderer}
 */
public class IconRenderer extends Application
{

	private static final Path OUT = Path.of("target/FOHanalyzer.iconset");

	/**
	 * {@code {pixels, filename}} — the set {@code iconutil} expects for a macOS
	 * icon.
	 */
	private static final Object[][] VARIANTS = {
		{ 16, "icon_16x16.png" },
		{ 32, "icon_16x16@2x.png" },
		{ 32, "icon_32x32.png" },
		{ 64, "icon_32x32@2x.png" },
		{ 128, "icon_128x128.png" },
		{ 256, "icon_128x128@2x.png" },
		{ 256, "icon_256x256.png" },
		{ 512, "icon_256x256@2x.png" },
		{ 512, "icon_512x512.png" },
		{ 1024, "icon_512x512@2x.png" },
	};

	/**
	 * Artwork inset inside the canvas, so the icon is not full-bleed against
	 * the Dock.
	 */
	private static final double FILL = 0.88;

	@Override
	public void start(Stage stage) throws Exception
	{
		Files.createDirectories(OUT);
		for (Object[] v : VARIANTS)
		{
			int px = (Integer)v[0];
			write(px, OUT.resolve((String)v[1]).toFile());
		}
		System.out.println("ICONSET_WROTE " + OUT.toAbsolutePath());
		Platform.exit();
	}

	private static void write(int px, File target) throws Exception
	{
		StackPane holder = new StackPane(new Logo(px * FILL));
		holder.setPrefSize(px, px);
		holder.setStyle("-fx-background-color: transparent;");
		// A Scene is needed for layout, but never shown — this renders
		// offscreen.
		new Scene(holder, px, px, Color.TRANSPARENT);
		holder.applyCss();
		holder.layout();

		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);
		WritableImage img = holder.snapshot(params, new WritableImage(px, px));
		ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png", target);
	}
}
