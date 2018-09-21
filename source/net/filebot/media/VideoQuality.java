package net.filebot.media;

import static java.util.Comparator.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.util.Comparator;
import java.util.regex.Pattern;

import net.filebot.format.MediaBindingBean;

public class VideoQuality implements Comparator<File> {

	public static final Comparator<File> DESCENDING_ORDER = new VideoQuality().reversed();

	public static boolean isBetter(File f1, File f2) {
		return DESCENDING_ORDER.compare(f1, f2) < 0;
	}

	private final Comparator<File> chain = comparing(f -> new MediaBindingBean(f, f), comparingInt(this::getRepack).thenComparingInt(this::getResolution).thenComparingLong(MediaBindingBean::getFileSize));

	@Override
	public int compare(File f1, File f2) {
		return chain.compare(f1, f2);
	}

	private final Pattern repack = releaseInfo.getRepackPattern();

	private int getRepack(MediaBindingBean m) {
		return find(m.getFileName(), repack) || find(m.getOriginalFileName(), repack) ? 1 : 0;
	}

	private int getResolution(MediaBindingBean m) {
		// use video file for video/subtitle pairs when comparing the subtitle file
		File mediaFile = m.getInferredMediaFile();

		if (VIDEO_FILES.accept(mediaFile)) {
			try {
				return m.getDimension().stream().mapToInt(Number::intValue).reduce((a, b) -> a * b).orElse(0);
			} catch (Exception e) {
				debug.warning("Failed to read media info: " + e);
			}
		}

		return 0;
	}

}
