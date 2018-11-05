package net.filebot.similarity;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.ibm.icu.text.Transliterator;

import net.filebot.format.BindingException;
import net.filebot.format.MediaBindingBean;
import net.filebot.media.SmartSeasonEpisodeMatcher;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.vfs.FileInfo;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeFormat;
import net.filebot.web.Movie;
import net.filebot.web.SeriesInfo;
import net.filebot.web.SimpleDate;

public enum EpisodeMetrics implements SimilarityMetric {

	// Match by season / episode numbers
	SeasonEpisode(new SeasonEpisodeMetric(new SmartSeasonEpisodeMatcher(null, false)) {

		private final Map<Object, Collection<SxE>> transformCache = synchronizedMap(new HashMap<Object, Collection<SxE>>(64, 4));

		@Override
		protected Collection<SxE> parse(Object object) {
			// SxE sets for Episode objects cannot be cached because the same Episode (by ID) may have different episode numbers depending on the order (e.g. Airdate VS DVD order)
			if (object instanceof Episode) {
				Episode episode = (Episode) object;
				return parse(episode);
			}

			if (object instanceof Movie) {
				return emptySet();
			}

			return transformCache.computeIfAbsent(object, super::parse);
		}

		private Set<SxE> parse(Episode e) {
			// get SxE from episode, both SxE for season/episode numbering and SxE for absolute episode numbering
			Set<SxE> sxe = new HashSet<SxE>(2);

			// default SxE numbering
			if (e.getEpisode() != null) {
				sxe.add(new SxE(e.getSeason(), e.getEpisode()));

				// absolute numbering
				if (e.getAbsolute() != null) {
					sxe.add(new SxE(null, e.getAbsolute()));
				}
			} else {
				// 0xSpecial numbering
				if (e.getSpecial() != null) {
					sxe.add(new SxE(0, e.getSpecial()));
				}
			}

			return sxe;
		}

	}),

	// Match episode airdate
	AirDate(new DateMetric(getDateMatcher()) {

		private final Map<Object, SimpleDate> transformCache = synchronizedMap(new HashMap<Object, SimpleDate>(64, 4));

		@Override
		public SimpleDate parse(Object object) {
			if (object instanceof Episode) {
				Episode episode = (Episode) object;
				return episode.getAirdate();
			}

			if (object instanceof Movie) {
				return null;
			}

			return transformCache.computeIfAbsent(object, super::parse);
		}
	}),

	// Match by episode/movie title
	Title(new SubstringMetric() {

		@Override
		protected String normalize(Object object) {
			if (object instanceof Episode) {
				Episode e = (Episode) object;

				// don't use title for matching if title equals series name
				if (e.getTitle() != null) {
					String title = normalizeObject(removeTrailingBrackets(e.getTitle()));
					if (title.length() >= 4 && !normalizeObject(e.getSeriesName()).contains(title)) {
						return title;
					}
				}
			}

			if (object instanceof Movie) {
				return normalizeObject(((Movie) object).getName());
			}

			String s = normalizeObject(object);
			return s.length() >= 4 ? s : null; // only consider long enough strings to avoid false matches
		}
	}),

	// Match by SxE and airdate
	EpisodeIdentifier(new MetricCascade(SeasonEpisode, AirDate)),

	// Advanced episode <-> file matching Lv1
	EpisodeFunnel(new MetricCascade(SeasonEpisode, AirDate, Title)),

	// Advanced episode <-> file matching Lv2
	EpisodeBalancer(new SimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			float sxe = EpisodeIdentifier.getSimilarity(o1, o2);
			float title = sxe < 1 ? Title.getSimilarity(o1, o2) : 1; // if SxE matches then boost score as if it was a title match as well

			// account for misleading SxE patterns in the episode title
			if (sxe < 0 && title == 1 && EpisodeIdentifier.getSimilarity(getTitle(o1), getTitle(o2)) == 1) {
				sxe = 1;
				title = 0;
			}

			// allow title to override SxE only if series name also is a good match
			if (title == 1 && SeriesName.getSimilarity(o1, o2) < 0.5f) {
				title = 0;
			}

			// 1:SxE && Title, 2:SxE
			return (float) ((Math.max(sxe, 0) * title) + (Math.floor(sxe) / 10));
		}

		public Object getTitle(Object o) {
			if (o instanceof Episode) {
				Episode e = (Episode) o;
				return e.getSeriesName() + " " + e.getTitle();
			}
			return o;
		}
	}),

	// Match series title and episode title against folder structure and file name
	SubstringFields(new SubstringMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String[] f1 = normalize(fields(o1));
			String[] f2 = normalize(fields(o2));

			// match all fields and average similarity
			double sum = 0;
			for (int i = 0; i < f1.length; i++) {
				for (int j = 0; j < f2.length; j++) {
					float f = super.getSimilarity(f1[i], f2[j]);
					if (f > 0) {
						// 2-sqrt(x) from 0 to 1
						double multiplier = 2 - Math.sqrt((double) (i + j) / (f1.length + f2.length));

						// bonus points for primary matches (e.g. primary title matches filename > alias title matches folder path)
						sum += f * multiplier;
					}
				}
			}
			sum /= f1.length * f2.length;

			return sum >= 0.9 ? 1 : sum >= 0.1 ? 0.5f : 0;
		}

		protected String[] normalize(Object[] objects) {
			// normalize objects (and make sure to keep word boundaries)
			return stream(objects).map(EpisodeMetrics::normalizeObject).toArray(String[]::new);
		}

		protected static final int MAX_FIELDS = 5;

		protected Object[] fields(Object object) {
			if (object instanceof Episode) {
				Episode e = (Episode) object;

				Stream<String> primaryNames = Stream.of(e.getSeriesName(), e.getTitle());
				Stream<String> aliasNames = e.getSeriesInfo() == null ? Stream.empty() : e.getSeriesInfo().getAliasNames().stream().limit(MAX_FIELDS);

				Stream<String> names = Stream.concat(primaryNames, aliasNames).filter(s -> s != null && s.length() > 0).map(Normalization::removeTrailingBrackets).distinct();
				return copyOf(names.limit(MAX_FIELDS).toArray(), MAX_FIELDS);
			}

			if (object instanceof File) {
				File f = (File) object;
				return new Object[] { f, f.getParentFile().getPath() };
			}

			if (object instanceof Movie) {
				Movie m = (Movie) object;
				return new Object[] { m.getName(), m.getYear() };
			}

			return new Object[] { object };
		}
	}),

	// Match via common word sequence in episode name and file name
	NameSubstringSequence(new SequenceMatchSimilarity() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String[] f1 = getNormalizedEffectiveIdentifiers(o1);
			String[] f2 = getNormalizedEffectiveIdentifiers(o2);

			// match all fields and average similarity
			float max = 0;
			for (String s1 : f1) {
				for (String s2 : f2) {
					max = Math.max(super.getSimilarity(s1, s2), max);
				}
			}

			// normalize absolute similarity to similarity rank (4 ranks in total),
			// so we are less likely to fall for false positives in this pass, and move on to the next one
			return (float) (Math.floor(max * 4) / 4);
		}

		@Override
		protected String normalize(Object object) {
			return object.toString();
		}

		protected String[] getNormalizedEffectiveIdentifiers(Object object) {
			List<?> identifiers = getEffectiveIdentifiers(object);
			String[] names = new String[identifiers.size()];

			for (int i = 0; i < names.length; i++) {
				names[i] = normalizeObject(identifiers.get(i));
			}
			return names;
		}

		protected List<?> getEffectiveIdentifiers(Object object) {
			if (object instanceof Episode) {
				return ((Episode) object).getSeriesNames();
			} else if (object instanceof Movie) {
				return ((Movie) object).getEffectiveNames();
			} else if (object instanceof File) {
				return listPathTail((File) object, 3, true);
			}
			return singletonList(object);
		}
	}),

	// Match by generic name similarity (round rank)
	Name(new NameSimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			// normalize absolute similarity to similarity rank (4 ranks in total),
			// so we are less likely to fall for false positives in this pass, and move on to the next one
			return (float) (Math.floor(super.getSimilarity(o1, o2) * 4) / 4);
		}

		@Override
		protected String normalize(Object object) {
			// simplify file name, if possible
			return normalizeObject(object);
		}
	}),

	// Match by generic name similarity (absolute)
	SeriesName(new NameSimilarityMetric() {

		private final SeriesNameMatcher seriesNameMatcher = getSeriesNameMatcher(false);

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String[] f1 = getNormalizedEffectiveIdentifiers(o1);
			String[] f2 = getNormalizedEffectiveIdentifiers(o2);

			// match all fields and average similarity
			float max = 0;
			for (String s1 : f1) {
				for (String s2 : f2) {
					max = Math.max(super.getSimilarity(s1, s2), max);
				}
			}

			// normalize absolute similarity to similarity rank (4 ranks in total),
			// so we are less likely to fall for false positives in this pass, and move on to the next one
			return (float) (Math.floor(max * 4) / 4);
		}

		@Override
		protected String normalize(Object object) {
			return object.toString();
		}

		protected String[] getNormalizedEffectiveIdentifiers(Object object) {
			return getEffectiveIdentifiers(object).stream().map(EpisodeMetrics::normalizeObject).toArray(String[]::new);
		}

		protected List<?> getEffectiveIdentifiers(Object object) {
			if (object instanceof Episode) {
				Episode episode = (Episode) object;

				// strip release info from known series name to make sure it matches the stripped filename
				return stripReleaseInfo(episode.getSeriesNames(), true);
			} else if (object instanceof File) {
				File file = (File) object;

				// guess potential series names from path
				return listPathTail(file, 3, true).stream().map(f -> {
					String fn = getName(f);
					String sn = seriesNameMatcher.matchByEpisodeIdentifier(fn);
					return sn != null ? sn : fn;
				}).collect(collectingAndThen(toList(), v -> stripReleaseInfo(v, true)));
			}

			return emptyList();
		}
	}),

	SeriesNameBalancer(new MetricCascade(NameSubstringSequence, Name, SeriesName)),

	// Match by generic name similarity (absolute)
	FilePath(new NameSimilarityMetric() {

		@Override
		protected String normalize(Object object) {
			if (object instanceof File) {
				object = normalizePathSeparators(getRelativePathTail((File) object, 3).getPath());
			}
			return normalizeObject(object.toString()); // simplify file name, if possible
		}
	}),

	FilePathBalancer(new NameSimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String s1 = normalizeObject(o1);
			String s2 = normalizeObject(o2);

			s1 = stripReleaseInfo(s1, false);
			s2 = stripReleaseInfo(s2, false);

			int length = Math.min(s1.length(), s2.length());
			s1 = s1.substring(0, length);
			s2 = s2.substring(0, length);

			return (float) (Math.floor(super.getSimilarity(s1, s2) * 4) / 4);
		};

		@Override
		protected String normalize(Object object) {
			return object.toString();
		}
	}),

	NumericSequence(new SequenceMatchSimilarity() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			float lowerBound = super.getSimilarity(normalize(o1, true), normalize(o2, true));
			float upperBound = super.getSimilarity(normalize(o1, false), normalize(o2, false));

			return Math.max(lowerBound, upperBound);
		};

		@Override
		protected String normalize(Object object) {
			return object.toString();
		};

		protected String normalize(Object object, boolean numbersOnly) {
			if (object instanceof Episode) {
				Episode e = (Episode) object;
				if (numbersOnly) {
					object = EpisodeFormat.SeasonEpisode.formatSxE(e);
				} else {
					object = String.format("%s %s", e.getSeriesName(), EpisodeFormat.SeasonEpisode.formatSxE(e));
				}
			} else if (object instanceof Movie) {
				Movie m = (Movie) object;
				if (numbersOnly) {
					object = m.getYear();
				} else {
					object = String.format("%s %s", m.getName(), m.getYear());
				}
			}

			// simplify file name if possible and extract numbers
			List<Integer> numbers = matchIntegers(normalizeObject(object));
			return join(numbers, " ");
		}
	}),

	// Match by generic numeric similarity
	Numeric(new NumericSimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			String[] f1 = fields(o1);
			String[] f2 = fields(o2);

			// match all fields and average similarity
			float max = 0;
			for (String s1 : f1) {
				for (String s2 : f2) {
					if (s1 != null && s2 != null) {
						max = Math.max(super.getSimilarity(s1, s2), max);
						if (max >= 1) {
							return max;
						}
					}
				}
			}
			return max;
		}

		protected String[] fields(Object object) {
			if (object instanceof Episode) {
				Episode episode = (Episode) object;
				String[] f = new String[3];
				f[0] = episode.getSeriesName();
				f[1] = episode.getSpecial() == null ? EpisodeFormat.SeasonEpisode.formatSxE(episode) : episode.getSpecial().toString();
				f[2] = episode.getAbsolute() == null ? null : episode.getAbsolute().toString();
				return f;
			}

			if (object instanceof Movie) {
				Movie movie = (Movie) object;
				return new String[] { movie.getName(), String.valueOf(movie.getYear()) };
			}

			return new String[] { normalizeObject(object) };
		}
	}),

	// Prioritize proper episodes over specials
	SpecialNumber(new SimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			return getSpecialFactor(o1) + getSpecialFactor(o2);
		}

		public int getSpecialFactor(Object object) {
			if (object instanceof Episode) {
				Episode episode = (Episode) object;
				return episode.getSpecial() != null ? -1 : 1;
			}
			return 0;
		}
	}),

	// Match by file length (only works when matching torrents or files)
	FileSize(new FileSizeMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			// order of arguments is logically irrelevant, but we might be able to save us a call to File.length() which is quite costly
			return o1 instanceof File ? super.getSimilarity(o2, o1) : super.getSimilarity(o1, o2);
		}

		@Override
		protected long getLength(Object object) {
			if (object instanceof FileInfo) {
				return ((FileInfo) object).getLength();
			}

			return super.getLength(object);
		}
	}),

	// Match by common words at the beginning of both files
	FileName(new FileNameMetric() {

		@Override
		protected String getFileName(Object object) {
			if (object instanceof File || object instanceof FileInfo) {
				return normalizeObject(object);
			}

			return null;
		}
	}),

	// Match by file last modified and episode release dates
	TimeStamp(new TimeStampMetric(10, ChronoUnit.YEARS) {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			// adjust differentiation accuracy to about 2.5 years
			float f = super.getSimilarity(o1, o2);

			return f >= 0.75 ? 1 : f >= 0 ? 0 : -1;
		}

		private long getTimeStamp(SimpleDate date) {
			// some episodes may not have a defined airdate
			if (date != null) {
				Instant t = date.toInstant();
				if (t.isBefore(Instant.now())) {
					return t.toEpochMilli();
				}
			}

			// big penalty for episodes not yet aired
			return -1;
		}

		private long getTimeStamp(File file) {
			if (VIDEO_FILES.accept(file) && file.length() > ONE_MEGABYTE) {
				try {
					return new MediaBindingBean(file, file).getEncodedDate().getTimeStamp();
				} catch (BindingException e) {
					debug.finest(e::getMessage); // Binding "General[0][Encoded_Date]": undefined => normal if Encoded_Date is undefined => ignore
				} catch (Exception e) {
					debug.warning("Failed to read media encoding date: " + e.getMessage());
				}
			}

			return super.getTimeStamp(file); // default to file creation date
		}

		@Override
		public long getTimeStamp(Object object) {
			if (object instanceof Episode) {
				Episode e = (Episode) object;
				return getTimeStamp(e.getAirdate());
			} else if (object instanceof Movie) {
				Movie m = (Movie) object;
				return getTimeStamp(new SimpleDate(m.getYear(), 1, 1));
			} else if (object instanceof File) {
				File file = (File) object;
				return getTimeStamp(file);
			}

			return -1;
		}
	}),

	SeriesRating(new SimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			float r1 = getScore(o1);
			float r2 = getScore(o2);

			if (r1 < 0 || r2 < 0)
				return -1;

			return Math.max(r1, r2);
		}

		public float getScore(Object object) {
			if (object instanceof Episode) {
				SeriesInfo seriesInfo = ((Episode) object).getSeriesInfo();
				if (seriesInfo != null && seriesInfo.getRating() != null && seriesInfo.getRatingCount() != null) {
					if (seriesInfo.getRatingCount() >= 20) {
						return (float) Math.floor(seriesInfo.getRating() / 3); // BOOST POPULAR SHOWS and PUT INTO 3 GROUPS
					}
					if (seriesInfo.getRatingCount() >= 1) {
						return 0; // PENALIZE SHOWS WITH FEW RATINGS
					}
					return -1; // BIG PENALTY FOR SHOWS WITH 0 RATINGS
				}
			}
			return 0;
		}
	}),

	VoteRate(new SimilarityMetric() {

		@Override
		public float getSimilarity(Object o1, Object o2) {
			float r1 = getScore(o1);
			float r2 = getScore(o2);

			return Math.max(r1, r2) >= 0.1 ? 1 : 0;
		}

		public float getScore(Object object) {
			if (object instanceof Episode) {
				SeriesInfo seriesInfo = ((Episode) object).getSeriesInfo();
				if (seriesInfo != null && seriesInfo.getRating() != null && seriesInfo.getRatingCount() != null && seriesInfo.getStartDate() != null) {
					long days = ChronoUnit.DAYS.between(seriesInfo.getStartDate().toLocalDate(), LocalDate.now());
					if (days > 0) {
						return (float) ((seriesInfo.getRatingCount().doubleValue() / days) * seriesInfo.getRating());
					}
				}
			}
			return 0;
		}
	}),

	// Match by (region) or (year) hints
	RegionHint(new SimilarityMetric() {

		private final Pattern hint = compile("[(](\\p{Alpha}+|\\p{Digit}+)[)]$");

		private final SeriesNameMatcher seriesNameMatcher = getSeriesNameMatcher(true);

		@Override
		public float getSimilarity(Object o1, Object o2) {
			Set<String> h1 = getHint(o1);
			Set<String> h2 = getHint(o2);

			return h1.isEmpty() || h2.isEmpty() ? 0 : h1.containsAll(h2) || h2.containsAll(h1) ? 1 : 0;
		}

		public Set<String> getHint(Object o) {
			if (o instanceof Episode) {
				for (String sn : ((Episode) o).getSeriesNames()) {
					Matcher m = hint.matcher(sn);
					if (m.find()) {
						return singleton(m.group(1).trim().toLowerCase());
					}
				}
			} else if (o instanceof File) {
				Set<String> h = new HashSet<String>();
				for (File f : listPathTail((File) o, 3, true)) {
					// try to focus on series name
					String fn = f.getName();
					String sn = seriesNameMatcher.matchByEpisodeIdentifier(fn);
					String[] tokens = PUNCTUATION_OR_SPACE.split(sn != null ? sn : fn);
					for (String s : tokens) {
						if (s.length() > 0) {
							h.add(s.trim().toLowerCase());
						}
					}
				}
				return h;
			}

			return emptySet();
		}
	}),

	// Match by stored MetaAttributes if possible
	MetaAttributes(new CrossPropertyMetric() {

		@Override
		protected Map<String, Object> getProperties(Object object) {
			// Episode / Movie objects
			if (object instanceof Episode || object instanceof Movie) {
				return super.getProperties(object);
			}

			// deserialize MetaAttributes if enabled and available
			if (object instanceof File) {
				Object metaObject = xattr.getMetaInfo((File) object);
				if (metaObject != null) {
					return super.getProperties(metaObject);
				}
			}

			// ignore everything else
			return emptyMap();
		}

	});

	// inner metric
	private final SimilarityMetric metric;

	private EpisodeMetrics(SimilarityMetric metric) {
		this.metric = metric;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		return metric.getSimilarity(o1, o2);
	}

	private static final Map<Object, String> transformCache = synchronizedMap(new HashMap<Object, String>(64, 4));
	private static final Transliterator transliterator = Transliterator.getInstance("Any-Latin;Latin-ASCII;[:Diacritic:]remove");

	public static String normalizeObject(Object object) {
		if (object == null) {
			return "";
		}

		return transformCache.computeIfAbsent(object, o -> {
			String name = normalizeFileName(o);

			// remove checksums, any [...] or (...)
			name = removeEmbeddedChecksum(name);

			// remove obvious release info
			name = stripFormatInfo(name);

			synchronized (transliterator) {
				name = transliterator.transform(name);
			}

			// remove or normalize special characters
			return normalizePunctuation(name).toLowerCase();
		});
	}

	private static String normalizeFileName(Object object) {
		if (object instanceof File) {
			return getName((File) object);
		} else if (object instanceof FileInfo) {
			return ((FileInfo) object).getName();
		}
		return object.toString();
	}

	public static SimilarityMetric[] defaultSequence(boolean includeFileMetrics) {
		// 1 pass: divide by file length (only works for matching torrent entries or files)
		// 2-3 pass: divide by title or season / episode numbers
		// 4 pass: divide by folder / file name and show name / episode title
		// 5 pass: divide by name (rounded into n levels)
		// 6 pass: divide by generic numeric similarity
		// 7 pass: prefer episodes that were aired closer to the last modified date of the file
		// 8 pass: resolve remaining collisions via absolute string similarity
		if (includeFileMetrics) {
			return new SimilarityMetric[] { FileSize, new MetricCascade(FileName, EpisodeFunnel), EpisodeBalancer, AirDate, MetaAttributes, SubstringFields, SeriesNameBalancer, SeriesName, RegionHint, SpecialNumber, Numeric, NumericSequence, SeriesRating, VoteRate, TimeStamp, FilePathBalancer, FilePath };
		} else {
			return new SimilarityMetric[] { EpisodeFunnel, EpisodeBalancer, AirDate, MetaAttributes, SubstringFields, SeriesNameBalancer, SeriesName, RegionHint, SpecialNumber, Numeric, NumericSequence, SeriesRating, VoteRate, TimeStamp, FilePathBalancer, FilePath };
		}
	}

	public static SimilarityMetric verificationMetric() {
		return new MetricCascade(FileName, SeasonEpisode, AirDate, Title, Name);
	}

}
