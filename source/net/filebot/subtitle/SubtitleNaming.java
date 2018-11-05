package net.filebot.subtitle;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.List;

import net.filebot.web.SubtitleDescriptor;

public enum SubtitleNaming {

	ORIGINAL {

		@Override
		public String format(File video, SubtitleDescriptor subtitle, String ext) {
			return String.format("%s.%s", subtitle.getName(), ext);
		}

		@Override
		public String toString() {
			return "Keep Original";
		}
	},

	MATCH_VIDEO {

		@Override
		public String format(File video, SubtitleDescriptor subtitle, String ext) {
			return SubtitleUtilities.formatSubtitle(getName(video), null, ext);
		}

		@Override
		public String toString() {
			return "Match Video";
		}
	},

	MATCH_VIDEO_ADD_LANGUAGE_TAG {

		@Override
		public String format(File video, SubtitleDescriptor subtitle, String ext) {
			return SubtitleUtilities.formatSubtitle(getName(video), subtitle.getLanguageName(), ext);
		}

		@Override
		public String toString() {
			return "Match Video and Language";
		}
	};

	public abstract String format(File video, SubtitleDescriptor subtitle, String ext);

	public static List<String> names() {
		return stream(values()).map(Enum::name).collect(toList());
	}

	public static SubtitleNaming forName(String name) {
		for (SubtitleNaming naming : values()) {
			if (naming.name().equalsIgnoreCase(name)) {
				return naming;
			}
		}

		throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
	}

}
