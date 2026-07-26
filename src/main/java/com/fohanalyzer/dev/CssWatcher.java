package com.fohanalyzer.dev;

import javafx.application.Platform;
import javafx.scene.Scene;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Dev aid: re-applies {@code theme.css} to a live {@link Scene} whenever the
 * source file changes, so padding/colour/font tweaks no longer cost an app
 * restart.
 *
 * <p>
 * Enabled with {@code FOH_DEV=true} and a no-op otherwise — nothing is watched
 * and no thread is started in a normal run. It reads the file under
 * {@code src/main/resources} rather than the copy in {@code target/classes},
 * because the source is what you edit.
 */
public final class CssWatcher
{

	/**
	 * Relative to the working directory, which is the project root under
	 * {@code mvn javafx:run}.
	 */
	private static final String SOURCE = "src/main/resources/com/fohanalyzer/theme.css";

	/**
	 * Editors save via temp-file-and-rename, emitting several events; let the
	 * burst settle.
	 */
	private static final long SETTLE_MS = 120;

	/** Previous temp copy, replaced on each reload. FX thread only. */
	private static Path previous;

	private CssWatcher()
	{
	}

	/**
	 * Start watching if {@code FOH_DEV=true}. Call on the FX thread, after the
	 * scene exists.
	 */
	public static void installIfEnabled(Scene scene)
	{
		if (!"true".equals(System.getenv("FOH_DEV"))) return;

		Path css = Path.of(SOURCE).toAbsolutePath();
		if (!Files.isRegularFile(css))
		{
			System.out.println("[dev] no stylesheet at " + css + " — CSS watch disabled");
			return;
		}

		apply(scene, css); // start from the source file, not the packaged copy
		Thread watcher = new Thread(() -> watch(scene, css), "css-watch");
		watcher.setDaemon(true);
		watcher.start();
		System.out.println("[dev] watching " + css);
	}

	private static void watch(Scene scene, Path css)
	{
		Path name = css.getFileName();
		try (WatchService ws = FileSystems.getDefault().newWatchService())
		{
			css.getParent().register(ws, ENTRY_MODIFY, ENTRY_CREATE);
			while (true)
			{
				WatchKey key = ws.take();
				boolean touched = key.pollEvents().stream().anyMatch(e -> name.equals(e.context()));
				Thread.sleep(SETTLE_MS);
				key.reset();
				if (touched) Platform.runLater(() -> apply(scene, css));
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (IOException e)
		{
			System.out.println("[dev] CSS watch stopped: " + e);
		}
	}

	/**
	 * Point the scene at a fresh temp copy of the stylesheet.
	 *
	 * <p>
	 * JavaFX keys its parsed-stylesheet cache on the URL string, so re-adding
	 * the same path — even after {@code clear()} — can return the stale parse.
	 * Handing it a file it has never seen sidesteps the cache entirely instead
	 * of relying on undocumented invalidation behaviour.
	 */
	private static void apply(Scene scene, Path css)
	{
		try
		{
			Path copy = Files.createTempFile("foh-theme-", ".css");
			copy.toFile().deleteOnExit();
			Files.copy(css, copy, StandardCopyOption.REPLACE_EXISTING);
			scene.getStylesheets().setAll(copy.toUri().toString());
			if (previous != null) Files.deleteIfExists(previous);
			previous = copy;
			System.out.println("[dev] theme.css reloaded");
		}
		catch (IOException e)
		{
			System.out.println("[dev] reload failed: " + e);
		}
	}
}
