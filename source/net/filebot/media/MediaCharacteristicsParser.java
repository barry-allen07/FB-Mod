package net.filebot.media;

import java.io.File;

import net.filebot.mediainfo.MediaInfo;
import net.filebot.util.SystemProperty;

public enum MediaCharacteristicsParser {

	libmediainfo, ffprobe;

	public static MediaCharacteristicsParser getDefault() {
		return SystemProperty.of("net.filebot.media.parser", MediaCharacteristicsParser::valueOf, libmediainfo).get();
	}

	public static MediaCharacteristics open(File f) throws Exception {
		switch (getDefault()) {
		case libmediainfo:
			return new MediaInfo().open(f);
		case ffprobe:
			return new FFProbe().open(f);
		}

		throw new IllegalStateException();
	}

}