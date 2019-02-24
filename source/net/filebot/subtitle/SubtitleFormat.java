
package net.filebot.subtitle;

import java.util.Scanner;

import net.filebot.MediaTypes;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

public enum SubtitleFormat {

	SubRip {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubRipReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubRip");
		}
	},

	MicroDVD {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new MicroDVDReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/MicroDVD");
		}
	},

	SubViewer {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubViewerReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubViewer");
		}
	},

	SubStationAlpha {

		@Override
		public SubtitleDecoder getDecoder() {
			return content -> new SubStationAlphaReader(new Scanner(content)).stream();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SubStationAlpha");
		}
	},

	SAMI {

		@Override
		public SubtitleDecoder getDecoder() {
			return new SamiDecoder();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("subtitle/SAMI");
		}
	};

	public abstract SubtitleDecoder getDecoder();

	public abstract ExtensionFileFilter getFilter();

}
