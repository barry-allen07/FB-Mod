package net.filebot.subtitle;

import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.similarity.EpisodeMetrics.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.filebot.media.MediaCharacteristics;
import net.filebot.media.MediaCharacteristicsParser;
import net.filebot.similarity.CrossPropertyMetric;
import net.filebot.similarity.EpisodeMetrics;
import net.filebot.similarity.MetricAvg;
import net.filebot.similarity.MetricCascade;
import net.filebot.similarity.NameSimilarityMetric;
import net.filebot.similarity.NumericSimilarityMetric;
import net.filebot.similarity.SequenceMatchSimilarity;
import net.filebot.similarity.SimilarityMetric;
import net.filebot.web.OpenSubtitlesSubtitleDescriptor;
import net.filebot.web.SubtitleDescriptor;

public enum SubtitleMetrics implements SimilarityMetric {

	// subtitle verification metric specifically excluding SxE mismatches
	AbsoluteSeasonEpisode(new SimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			float f = SeasonEpisode.getSimilarity(o1, o2);
			if (f == 0 && (getEpisodeIdentifier(o1.toString(), true) == null) == (getEpisodeIdentifier(o2.toString(), true) == null)) {
				return 0;
			}
			return f < 1 ? -1 : 1;
		}
	}),

	DiskNumber(new NumericSimilarityMetric() {

		private final Pattern CDNO = Pattern.compile("(?:CD|DISK)(\\d+)", Pattern.CASE_INSENSITIVE);

		@Override
		public float getSimilarity(Object o1, Object o2) {
			int c1 = getDiskNumber(o1);
			int c2 = getDiskNumber(o2);

			if (c1 == 0 && c2 == 0) // undefined
				return 0;

			return c1 == c2 ? 1 : -1; // positive or negative match
		}

		public int getDiskNumber(Object o) {
			int cd = 0;
			Matcher matcher = CDNO.matcher(o.toString());
			while (matcher.find()) {
				cd = Integer.parseInt(matcher.group(1));
			}
			return cd;
		}
	}),

	NameSubstringSequenceExists(new SequenceMatchSimilarity() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String[] f1 = getNormalizedEffectiveIdentifiers(o1);
			String[] f2 = getNormalizedEffectiveIdentifiers(o2);

			for (String s1 : f1) {
				for (String s2 : f2) {
					if (super.getSimilarity(s1, s2) >= 1) {
						return 1;
					}
				}
			}

			return 0;
		}

		@Override
		protected float similarity(String match, String s1, String s2) {
			return match.length() > 0 ? 1 : 0;
		}

		@Override
		protected String normalize(Object object) {
			return object.toString();
		}

		protected String[] getNormalizedEffectiveIdentifiers(Object object) {
			List<?> identifiers = getEffectiveIdentifiers(object);
			String[] names = new String[identifiers.size()];

			for (int i = 0; i < names.length; i++) {
				names[i] = EpisodeMetrics.normalizeObject(identifiers.get(i));
			}

			return names;
		}

		protected List<?> getEffectiveIdentifiers(Object object) {
			if (object instanceof OpenSubtitlesSubtitleDescriptor) {
				return singletonList(((OpenSubtitlesSubtitleDescriptor) object).getName());
			} else if (object instanceof File) {
				return listPathTail((File) object, 2, true);
			}
			return emptyList();
		}
	}),

	OriginalFileName(new SequenceMatchSimilarity() {

		@Override
		protected float similarity(String match, String s1, String s2) {
			return (float) match.length() / Math.max(s1.length(), s2.length()) > 0.8 ? 1 : 0;
		}

		@Override
		public String normalize(Object object) {
			if (object instanceof File) {
				File file = (File) object;
				String name = xattr.getOriginalName(file);
				if (name == null) {
					name = file.getName();
				}
				return super.normalize(getNameWithoutExtension(name));
			} else if (object instanceof OpenSubtitlesSubtitleDescriptor) {
				String name = ((OpenSubtitlesSubtitleDescriptor) object).getName();
				return super.normalize(name);
			}
			return super.normalize(object);
		}
	}),

	VideoProperties(new CrossPropertyMetric() {

		private final String FPS = "FPS";
		private final String SECONDS = "SECS";

		@Override
		public float getSimilarity(Object o1, Object o2) {
			return o1 instanceof SubtitleDescriptor ? super.getSimilarity(o1, o2) : super.getSimilarity(o2, o1); // make sure that SubtitleDescriptor is o1
		};

		@Override
		protected Map<String, Object> getProperties(Object object) {
			if (object instanceof OpenSubtitlesSubtitleDescriptor) {
				return getSubtitleProperties((OpenSubtitlesSubtitleDescriptor) object);
			} else if (object instanceof File) {
				return getVideoProperties((File) object);
			}
			return emptyMap();
		};

		private Map<String, Object> getProperties(float fps, long millis) {
			Map<String, Object> props = new HashMap<String, Object>(2);
			if (fps > 0) {
				props.put(FPS, Math.round(fps)); // round because most FPS values in the database are bad anyway
			}
			if (millis > 0) {
				props.put(SECONDS, Math.round(Math.floor(millis / 1000d)));
			}
			return props;
		}

		private Map<String, Object> getSubtitleProperties(OpenSubtitlesSubtitleDescriptor subtitle) {
			try {
				return getProperties(subtitle.getMovieFPS(), subtitle.getMovieTimeMS());
			} catch (Exception e) {
				debug.warning("Failed to read subtitle properties: " + e);
			}
			return emptyMap();
		}

		private final Map<File, Map<String, Object>> mediaInfoCache = synchronizedMap(new WeakHashMap<File, Map<String, Object>>(64));

		private Map<String, Object> getVideoProperties(File file) {
			return mediaInfoCache.computeIfAbsent(file, f -> {
				try (MediaCharacteristics mi = MediaCharacteristicsParser.open(f)) {
					return getProperties(mi.getFrameRate(), mi.getDuration().toMillis());
				} catch (Exception e) {
					debug.warning("Failed to read video properties: " + e.getMessage());
				}
				return emptyMap();
			});
		}
	});

	// inner metric
	private final SimilarityMetric metric;

	private SubtitleMetrics(SimilarityMetric metric) {
		this.metric = metric;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		return metric.getSimilarity(o1, o2);
	}

	public static SimilarityMetric[] defaultSequence() {
		return new SimilarityMetric[] { EpisodeFunnel, EpisodeBalancer, OriginalFileName, NameSubstringSequenceExists, new MetricAvg(NameSubstringSequenceExists, Name), Numeric, FileName, DiskNumber, VideoProperties, new NameSimilarityMetric() };
	}

	public static SimilarityMetric verificationMetric() {
		return new MetricCascade(AbsoluteSeasonEpisode, AirDate, new MetricAvg(NameSubstringSequenceExists, Name), getMovieMatchMetric(), OriginalFileName);
	}

}
