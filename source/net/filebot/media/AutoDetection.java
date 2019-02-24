package net.filebot.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.WebServices.*;
import static net.filebot.format.ExpressionFormatMethods.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.filebot.similarity.NameSimilarityMetric;
import net.filebot.util.FastFile;
import net.filebot.web.Episode;
import net.filebot.web.Movie;

public class AutoDetection {

	private File[] files;
	private Locale locale;

	public AutoDetection(Collection<File> root, boolean resolve, Locale locale) {
		this.locale = locale;

		// require a set of distinct files
		this.files = root.stream().sorted().distinct().map(FastFile::new).toArray(File[]::new);

		// resolve folders if required
		if (resolve) {
			this.files = resolve(stream(files), getSystemFilesFilter()).toArray(File[]::new);
		}
	}

	protected Stream<File> resolve(Stream<File> root, FileFilter excludes) {
		return root.flatMap(f -> {
			if (f.isHidden() || excludes.accept(f)) {
				return Stream.empty();
			}
			if (f.isFile()) {
				return Stream.of(f);
			}
			if (f.isDirectory()) {
				return isDiskFolder(f) ? Stream.of(f) : resolve(getChildren(f).stream(), excludes);
			}
			return Stream.empty();
		});
	}

	public List<File> getFiles() {
		return unmodifiableList(asList(files));
	}

	private static final Pattern MOVIE_FOLDER_PATTERN = compile("Movies", CASE_INSENSITIVE);
	private static final Pattern SERIES_FOLDER_PATTERN = compile("TV.Shows|TV.Series|Season.\\d+", CASE_INSENSITIVE);
	private static final Pattern ANIME_FOLDER_PATTERN = compile("Anime", CASE_INSENSITIVE);

	private static final Pattern ABSOLUTE_EPISODE_PATTERN = compile("(?<!\\p{Alnum})E[P]?\\d{1,3}(?!\\p{Alnum})", CASE_INSENSITIVE);
	private static final Pattern SERIES_EPISODE_PATTERN = compile("(?<!\\p{Alnum})(tv[sp]|Season\\D?\\d{1,2}|\\d{4}.S\\d{2})(?!\\p{Alnum})", CASE_INSENSITIVE);
	private static final Pattern ANIME_EPISODE_PATTERN = compile("^\\[[^\\]]+Subs\\]", CASE_INSENSITIVE);

	private static final Pattern JAPANESE_AUDIO_LANGUAGE_PATTERN = compile("jpn|Japanese", CASE_INSENSITIVE);
	private static final Pattern JAPANESE_SUBTITLE_CODEC_PATTERN = compile("ASS|SSA", CASE_INSENSITIVE);

	public boolean isMusic(File f) {
		return AUDIO_FILES.accept(f) && !VIDEO_FILES.accept(f);
	}

	public boolean isMovie(File f) {
		return anyMatch(f.getParentFile(), MOVIE_FOLDER_PATTERN) || MediaDetection.isMovie(f, true);
	}

	public boolean isEpisode(File f) {
		if (anyMatch(f.getParentFile(), SERIES_FOLDER_PATTERN) || find(f.getPath(), SERIES_EPISODE_PATTERN)) {
			return true;
		}

		if (MediaDetection.isEpisode(f.getPath(), true)) {
			return true;
		}

		Object metaInfo = xattr.getMetaInfo(f);
		if (metaInfo instanceof Episode) {
			return !AniDB.getIdentifier().equals(((Episode) metaInfo).getSeriesInfo().getDatabase()); // return true for known non-Anime Episode objects
		}

		return false;
	}

	public boolean isAnime(File f) {
		if (parseEpisodeNumber(f.getName(), false) == null) {
			return false;
		}

		if (anyMatch(f.getParentFile(), ANIME_FOLDER_PATTERN) || find(f.getName(), ANIME_EPISODE_PATTERN) || find(f.getName(), EMBEDDED_CHECKSUM)) {
			return true;
		}

		if (VIDEO_FILES.accept(f) && f.length() > ONE_MEGABYTE) {
			// check for Japanese audio or characteristic subtitles
			try (MediaCharacteristics mi = MediaCharacteristicsParser.DEFAULT.open(f)) {
				return mi.getDuration().toMinutes() < 60 || find(mi.getAudioLanguage(), JAPANESE_AUDIO_LANGUAGE_PATTERN) && find(mi.getSubtitleCodec(), JAPANESE_SUBTITLE_CODEC_PATTERN);
			} catch (Exception e) {
				debug.warning("Failed to read audio language: " + e.getMessage());
			}
		}

		Object metaInfo = xattr.getMetaInfo(f);
		return metaInfo instanceof Episode && AniDB.getIdentifier().equals(((Episode) metaInfo).getSeriesInfo().getDatabase());
	}

	public boolean anyMatch(File file, Pattern pattern) {
		// episode characteristics override movie characteristics (e.g. episodes in ~/Movies folder which is considered a volume root)
		for (File f = file; f != null && !isVolumeRoot(f); f = f.getParentFile()) {
			if (pattern.matcher(f.getName()).matches()) {
				return true;
			}
		}
		return false;
	}

	public Map<Group, Set<File>> group() {
		Map<Group, Set<File>> groups = new LinkedHashMap<Group, Set<File>>();

		for (File file : files) {
			try {
				Group group = detectGroup(file);
				groups.computeIfAbsent(group, g -> new LinkedHashSet<File>()).add(new File(file.getPath())); // use FastFile internally but do not expose to outside code that expects File objects
			} catch (Exception e) {
				debug.log(Level.SEVERE, e, e::toString);
			}
		}

		return groups;
	}

	public Map<Group, Set<File>> groupParallel(ExecutorService threadPool) {
		Map<Group, Set<File>> groups = new LinkedHashMap<Group, Set<File>>();

		stream(files).collect(toMap(f -> f, f -> threadPool.submit(() -> detectGroup(f)), (a, b) -> a, LinkedHashMap::new)).forEach((file, group) -> {
			try {
				groups.computeIfAbsent(group.get(), k -> new LinkedHashSet<File>()).add(new File(file.getPath())); // use FastFile internally but do not expose to outside code that expects File objects
			} catch (Exception e) {
				debug.log(Level.SEVERE, e.getMessage(), e);
			}
		});

		return groups;
	}

	private Group detectGroup(File f) throws Exception {
		Group group = new Group();

		if (isMusic(f))
			return group.music(f);
		if (isMovie(f))
			return group.movie(getMovieMatches(f));
		if (isEpisode(f))
			return group.series(getSeriesMatches(f, false));
		if (isAnime(f))
			return group.anime(getSeriesMatches(f, true));

		// ignore movie matches if filename looks like an episode
		if (find(f.getName(), ABSOLUTE_EPISODE_PATTERN))
			return group.series(getSeriesMatches(f, false));

		// Movie VS Episode
		List<Movie> m = getMovieMatches(f);
		List<String> s = getSeriesMatches(f, false);

		if (m.isEmpty() && s.isEmpty())
			return group;
		if (s.size() > 0 && m.isEmpty())
			return group.series(s);
		if (m.size() > 0 && s.isEmpty())
			return group.movie(m);

		return new Rules(f, s, m).apply();
	}

	private List<String> getSeriesMatches(File f, boolean anime) throws Exception {
		List<String> names = detectSeriesNames(singleton(f), anime, locale);
		if (names.isEmpty()) {
			List<File> episodes = getVideoFiles(f.getParentFile());
			if (episodes.size() >= 5) {
				names = detectSeriesNames(episodes, anime, locale);
			}
		}
		return names;
	}

	private List<Movie> getMovieMatches(File file) throws Exception {
		return detectMovie(file, TheMovieDB, locale, false);
	}

	private List<File> getVideoFiles(File parent) {
		return stream(files).filter(it -> parent.equals(it.getParentFile())).filter(VIDEO_FILES::accept).collect(toList());
	}

	private static final Pattern YEAR = compile("\\D(?:19|20)\\d{2}\\D");
	private static final Pattern EPISODE_NUMBERS = compile("\\b\\d{1,3}\\b");
	private static final Pattern DASH = compile("^.{0,3}\\s[-]\\s.+$", UNICODE_CHARACTER_CLASS);
	private static final Pattern NUMBER_PAIR = compile("\\D\\d{1,2}\\D{1,3}\\d{1,2}\\D");
	private static final Pattern NON_NUMBER_NAME = compile("^[\\p{L}\\p{Space}\\p{Punct}]+$", UNICODE_CHARACTER_CLASS);

	private class Rules {

		private final Group group;

		private final File f;
		private final String s;
		private final Movie m;

		private final String dn, fn, sn, mn, asn;
		private final Pattern snm, mnm, mym;

		public Rules(File file, List<String> series, List<Movie> movie) throws Exception {
			group = new Group().series(series).movie(movie);

			f = file;
			s = series.get(0);
			m = movie.get(0);

			dn = normalize(getName(guessMovieFolder(f)));
			fn = normalize(getName(f));
			sn = normalize(s);
			mn = normalize(m.getName());

			snm = compile(sn, LITERAL);
			mnm = compile(mn, LITERAL);
			mym = compile(Integer.toString(m.getYear()), LITERAL);
			asn = after(fn, snm).orElse(fn);
		}

		private String normalize(String self) {
			return self == null ? "" : replaceSpace(normalizePunctuation(ascii(self)).toLowerCase(), " ").trim();
		}

		private float getSimilarity(String self, String other) {
			return new NameSimilarityMetric().getSimilarity(self, other);
		}

		private boolean matchMovie(String name) {
			return find(name, YEAR) && !matchMovieName(singleton(name), true, 0).isEmpty();
		}

		public Group apply() throws Exception {
			List<Rule> rules = new ArrayList<Rule>(15);
			rules.add(new Rule(-1, 0, this::equalsMovieName, "AutoDetection::equalsMovieName"));
			rules.add(new Rule(-1, 0, this::containsMovieYear, "AutoDetection::containsMovieYear"));
			rules.add(new Rule(-1, 0, this::containsMovieNameYear, "AutoDetection::containsMovieNameYear"));
			rules.add(new Rule(5, -1, this::containsEpisodeNumbers, "AutoDetection::containsEpisodeNumbers"));
			rules.add(new Rule(5, -1, this::commonNumberPattern, "AutoDetection::commonNumberPattern"));
			rules.add(new Rule(1, -1, this::episodeWithoutNumbers, "AutoDetection::episodeWithoutNumbers"));
			rules.add(new Rule(1, -1, this::episodeNumbers, "AutoDetection::episodeNumbers"));
			rules.add(new Rule(-1, 1, this::hasImdbId, "AutoDetection::hasImdbId"));
			rules.add(new Rule(-1, 1, this::nonNumberName, "AutoDetection::nonNumberName"));
			rules.add(new Rule(-1, 5, this::exactMovieMatch, "AutoDetection::exactMovieMatch"));
			rules.add(new Rule(-1, 1, this::containsMovieName, "AutoDetection::containsMovieName"));
			rules.add(new Rule(-1, 1, this::similarNameYear, "AutoDetection::similarNameYear"));
			rules.add(new Rule(-1, 1, this::similarNameNoNumbers, "AutoDetection::similarNameNoNumbers"));
			rules.add(new Rule(-1, 1, this::aliasNameMatch, "AutoDetection::aliasNameMatch"));

			int score_s = 0;
			int score_m = 0;
			for (Rule rule : rules) {
				if (rule.test()) {
					score_s += rule.s;
					score_m += rule.m;

					debug.finest(format("[+] %s", rule));

					if (score_s >= 1 && score_m <= -1) {
						debug.fine(format("[X] Rule as Series", score_s, score_m));
						return group.movie(null);
					}
					if (score_m >= 1 && score_s <= -1) {
						debug.fine(format("[X] Rule as Movie", score_s, score_m));
						return group.series(null);
					}
				} else {
					debug.finest(format("[-] %s", rule));
				}
			}
			return group;
		}

		public boolean equalsMovieName() {
			return mn.equals(fn);
		}

		public boolean containsMovieYear() {
			return m.getYear() >= 1950 && listPathTail(f, 3, true).stream().anyMatch(it -> {
				return after(it.getName(), mym).map(amy -> parseEpisodeNumber(amy, false) == null).orElse(false);
			});
		}

		public boolean containsMovieNameYear() {
			return find(mn, snm) && Stream.of(dn, fn).anyMatch(it -> {
				return after(it, YEAR).map(ay -> {
					return parseEpisodeNumber(ay, false) == null;
				}).orElse(false);
			});
		}

		public boolean containsEpisodeNumbers() {
			return parseEpisodeNumber(fn, true) != null || parseDate(fn) != null;
		}

		public boolean commonNumberPattern() {
			return getChildren(f.getParentFile(), VIDEO_FILES).stream().filter(it -> {
				return find(dn, snm) || find(normalize(it.getName()), snm);
			}).map(it -> {
				return streamMatches(it.getName(), EPISODE_NUMBERS).map(Integer::parseInt).collect(toSet());
			}).filter(it -> it.size() > 0).distinct().count() >= 10;
		}

		public boolean episodeWithoutNumbers() {
			return find(asn, DASH) && !matchMovie(fn);
		}

		public boolean episodeNumbers() {
			String n = stripReleaseInfo(asn, false);
			if (parseEpisodeNumber(n, false) != null || find(n, NUMBER_PAIR)) {
				return Stream.of(dn, fn).anyMatch(it -> find(it, snm) && !matchMovie(it));
			}
			return false;
		}

		public boolean hasImdbId() {
			return grepImdbId(fn).size() > 0;
		}

		public boolean nonNumberName() {
			return find(getName(f), NON_NUMBER_NAME);
		}

		public boolean exactMovieMatch() throws Exception {
			List<Movie> matches = detectMovieWithYear(f, TheMovieDB, Locale.US, true);
			return matches != null && !matches.isEmpty();
		}

		public boolean containsMovieName() {
			return fn.contains(mn) && parseEpisodeNumber(after(fn, mnm).orElse(fn), false) == null;
		}

		public boolean similarNameYear() {
			return getSimilarity(mn, fn) >= 0.8f || Stream.of(dn, fn).anyMatch(it -> {
				return matchIntegers(it).stream().filter(y -> m.getYear() - 1 <= y && y <= m.getYear() + 1).count() > 0;
			});
		}

		public boolean similarNameNoNumbers() {
			return Stream.of(dn, fn).anyMatch(it -> {
				return find(it, mnm) && !find(after(it, mnm).orElse(it), EPISODE_NUMBERS) && getSimilarity(it, mn) >= 0.2f + getSimilarity(it, sn);
			});
		}

		public boolean aliasNameMatch() {
			return m.getEffectiveNamesWithoutYear().stream().map(this::normalize).anyMatch(fn::contains);
		}

	}

	@FunctionalInterface
	private interface Test {
		boolean test() throws Exception;
	}

	private static class Rule implements Test {

		public final int s;
		public final int m;

		private final Test t;
		private final String name;

		public Rule(int s, int m, Test t, String name) {
			this.s = s;
			this.m = m;
			this.t = t;
			this.name = name;
		}

		@Override
		public boolean test() throws Exception {
			return t.test();
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public enum Type {
		Movie, Series, Anime, Music;
	}

	public static class Group extends EnumMap<Type, Object> {

		public Group() {
			super(Type.class);
		}

		public Group movie(List<Movie> movies) {
			put(Type.Movie, movies == null || movies.isEmpty() ? null : movies.get(0));
			return this;
		}

		public Group series(List<String> names) {
			put(Type.Series, names == null || names.isEmpty() ? null : replaceSpace(normalizePunctuation(names.get(0)).toLowerCase(), " ").trim());
			return this;

		}

		public Group anime(List<String> names) {
			put(Type.Anime, names == null || names.isEmpty() ? null : replaceSpace(normalizePunctuation(names.get(0)).toLowerCase(), " ").trim());
			return this;

		}

		public Group music(File f) {
			put(Type.Music, f == null ? null : f.getParent());
			return this;
		}

		public Object getMovie() {
			return get(Type.Movie);
		}

		public Object getSeries() {
			return get(Type.Series);
		}

		public Object getAnime() {
			return get(Type.Anime);
		}

		public Object getMusic() {
			return get(Type.Music);
		}

		public Group setMovie() {
			put(Type.Movie, Boolean.TRUE);
			return this;
		}

		public Group setSeries() {
			put(Type.Series, Boolean.TRUE);
			return this;

		}

		public Group setAnime() {
			put(Type.Anime, Boolean.TRUE);
			return this;
		}

		public Group setMusic() {
			put(Type.Music, Boolean.TRUE);
			return this;
		}

		public boolean isMovie() {
			return get(Type.Movie) != null;
		}

		public boolean isSeries() {
			return get(Type.Series) != null;
		}

		public boolean isAnime() {
			return get(Type.Anime) != null;
		}

		public boolean isMusic() {
			return get(Type.Music) != null;
		}

		public Type[] types() {
			return entrySet().stream().filter(it -> it.getValue() != null).map(it -> it.getKey()).toArray(Type[]::new);
		}
	}

}
