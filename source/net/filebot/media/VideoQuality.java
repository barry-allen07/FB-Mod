package net.filebot.media;

import static java.util.Comparator.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.FileUtilities.*;
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

	private final Comparator<File> chain = comparingInt(this::getRepack).thenComparingInt(this::getResolution).thenComparingLong(File::length);

	@Override
	public int compare(File f1, File f2) {
		return chain.compare(f1, f2);
	}

	private final Pattern repack = releaseInfo.getRepackPattern();

	private int getRepack(File f) {
		return find(f.getName(), repack) || find(xattr.getOriginalName(f), repack) ? 1 : 0;
	}

	private int getResolution(File f) {
		// use primary video file when checking video resolution of subtitle files or disk folders
		f = new MediaBindingBean(f, f).getInferredMediaFile();

		if (VIDEO_FILES.accept(f) && f.length() > ONE_MEGABYTE) {
			try (MediaCharacteristics mi = MediaCharacteristicsParser.DEFAULT.open(f)) {
				return mi.getWidth() * mi.getHeight();
			} catch (Exception e) {
				debug.warning("Failed to read video resolution: " + e.getMessage());
			}
		}

		return 0;
	}

}
