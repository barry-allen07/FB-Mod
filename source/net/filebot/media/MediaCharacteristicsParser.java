package net.filebot.media;

import java.io.File;

import net.filebot.mediainfo.MediaInfo;
import net.filebot.util.SystemProperty;

public enum MediaCharacteristicsParser {

	libmediainfo {

		@Override
		public MediaCharacteristics open(File f) throws Exception {
			return new MediaInfo().open(f);
		}
	},

	ffprobe {

		@Override
		public MediaCharacteristics open(File f) throws Exception {
			return new FFProbe().open(f);
		}
	};

	public abstract MediaCharacteristics open(File f) throws Exception;

	public static MediaCharacteristicsParser getDefault() {
		return DEFAULT;
	}

	public static final MediaCharacteristicsParser DEFAULT = SystemProperty.of("net.filebot.media.parser", MediaCharacteristicsParser::valueOf, libmediainfo).get();

}
