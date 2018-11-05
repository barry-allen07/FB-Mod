package net.filebot.media;

import static net.filebot.Logging.*;

import java.io.File;
import java.io.FileFilter;

public class MediaDurationFilter implements FileFilter {

	private final long min;
	private final long max;
	private final boolean acceptByDefault;

	public MediaDurationFilter(long min) {
		this(min, Long.MAX_VALUE, false);
	}

	public MediaDurationFilter(long min, long max, boolean acceptByDefault) {
		this.min = min;
		this.max = max;
		this.acceptByDefault = acceptByDefault;
	}

	public long getDuration(File f) {
		try (MediaCharacteristics mi = MediaCharacteristicsParser.open(f)) {
			return mi.getDuration().toMillis();
		} catch (Exception e) {
			debug.warning(format("Failed to read video duration: %s", e.getMessage()));
		}
		return -1;
	}

	@Override
	public boolean accept(File f) {
		long d = getDuration(f);
		if (d >= 0) {
			return d >= min && d <= max;
		}
		return acceptByDefault;
	}
}
