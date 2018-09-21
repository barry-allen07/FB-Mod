package net.filebot.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.similarity.CommonSequenceMatcher.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.CollationKey;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import net.filebot.ApplicationFolder;
import net.filebot.Language;
import net.filebot.Resource;
import net.filebot.WebServices;
import net.filebot.archive.Archive;
import net.filebot.similarity.DateMatcher;
import net.filebot.similarity.EpisodeMetrics;
import net.filebot.similarity.MetricAvg;
import net.filebot.similarity.NameSimilarityMetric;
import net.filebot.similarity.NumericSimilarityMetric;
import net.filebot.similarity.SeasonEpisodeMatcher;
import net.filebot.similarity.SeasonEpisodeMatcher.SeasonEpisodePattern;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.similarity.SequenceMatchSimilarity;
import net.filebot.similarity.SeriesNameMatcher;
import net.filebot.similarity.SimilarityComparator;
import net.filebot.similarity.SimilarityMetric;
import net.filebot.similarity.StringEqualsMetric;
import net.filebot.vfs.FileInfo;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.MovieIdentificationService;
import net.filebot.web.SearchResult;
import net.filebot.web.SeriesInfo;
import net.filebot.web.SimpleDate;

public class MediaDetection {

	public static final ReleaseInfo releaseInfo = new ReleaseInfo();

	public static FileFilter getSystemFilesFilter() {
		return releaseInfo.getSystemFilesFilter();
	}

	public static FileFilter getDiskFolderFilter() {
		return releaseInfo.getDiskFolderFilter();
	}

	public static FileFilter getClutterFileFilter() {
		return releaseInfo.getClutterFileFilter();
	}

	public static boolean isDiskFolder(File folder) {
		return getDiskFolderFilter().accept(folder);
	}

	public static boolean isClutterFile(File file) throws IOException {
		return getClutterFileFilter().accept(file);
	}

	public static boolean isVideoDiskFile(File file) throws Exception {
		if (file.isFile() && file.length() > ONE_MEGABYTE) {
			try (Archive iso = Archive.open(file)) {
				FileFilter diskFolderEntryFilter = releaseInfo.getDiskFolderEntryFilter();

				for (FileInfo it : iso.listFiles()) {
					for (File entry : listPath(it.toFile())) {
						if (diskFolderEntryFilter.accept(entry)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public static Locale guessLanguageFromSuffix(File file) {
		return releaseInfo.getSubtitleLanguageTag(getName(file));
	}

	public static boolean isEpisode(File file, boolean strict) {
		Object metaInfo = xattr.getMetaInfo(file);
		if (metaInfo instanceof Episode) {
			return true;
		}

		// require a good S00E00 match
		return isEpisode(String.join("/", file.getParent(), file.getName()), strict);
	}

	public static boolean isMovie(File file, boolean strict) {
		Object metaInfo = xattr.getMetaInfo(file);
		if (metaInfo != null) {
			return metaInfo instanceof Movie;
		}

		if (isEpisode(file, true)) {
			return false;
		}

		if (matchMovieName(asList(file.getName(), file.getParent()), strict, 0).size() > 0) {
			return true;
		}

		// check for valid imdb id patterns
		return grepImdbId(file.getPath()).stream().map(Movie::new).filter(m -> {
			try {
				return strict ? WebServices.TheMovieDB.getMovieDescriptor(m, Locale.US).getId() > 0 : true;
			} catch (Exception e) {
				return false;
			}
		}).filter(Objects::nonNull).findFirst().isPresent();
	}

	private static final SeasonEpisodeMatcher seasonEpisodeMatcherStrict = new SmartSeasonEpisodeMatcher(SeasonEpisodeMatcher.DEFAULT_SANITY, true);
	private static final SeasonEpisodeMatcher seasonEpisodeMatcherNonStrict = new SmartSeasonEpisodeMatcher(SeasonEpisodeMatcher.DEFAULT_SANITY, false);
	private static final DateMatcher dateMatcher = new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.ENGLISH, Locale.getDefault());

	public static SeasonEpisodeMatcher getSeasonEpisodeMatcher(boolean strict) {
		return strict ? seasonEpisodeMatcherStrict : seasonEpisodeMatcherNonStrict;
	}

	public static DateMatcher getDateMatcher() {
		return dateMatcher;
	}

	public static SeriesNameMatcher getSeriesNameMatcher(boolean strict) {
		return new SeriesNameMatcher(strict ? seasonEpisodeMatcherStrict : seasonEpisodeMatcherNonStrict, dateMatcher);
	}

	public static boolean isEpisode(String name, boolean strict) {
		return parseEpisodeNumber(name, strict) != null || parseDate(name) != null;
	}

	public static List<SxE> parseEpisodeNumber(String string, boolean strict) {
		return getSeasonEpisodeMatcher(strict).match(string);
	}

	public static List<SxE> parseEpisodeNumber(File file, boolean strict) {
		return getSeasonEpisodeMatcher(strict).match(file);
	}

	public static SimpleDate parseDate(Object object) {
		if (object instanceof File) {
			return getDateMatcher().match((File) object);
		}
		return getDateMatcher().match(object.toString());
	}

	public static Map<Set<File>, Set<String>> mapSeriesNamesByFiles(Collection<File> files, Locale locale, boolean anime) throws Exception {
		// map series names by folder
		Map<File, Set<String>> seriesNamesByFolder = new HashMap<File, Set<String>>();
		Map<File, List<File>> filesByFolder = mapByFolder(files);

		for (Entry<File, List<File>> it : filesByFolder.entrySet()) {
			Set<String> namesForFolder = new TreeSet<String>(getLenientCollator(locale));
			namesForFolder.addAll(detectSeriesNames(it.getValue(), anime, locale));

			seriesNamesByFolder.put(it.getKey(), namesForFolder);
		}

		// reverse map folders by series name
		Map<String, Set<File>> foldersBySeriesName = new HashMap<String, Set<File>>();

		for (Set<String> nameSet : seriesNamesByFolder.values()) {
			for (String name : nameSet) {
				Set<File> foldersForSeries = new HashSet<File>();
				for (Entry<File, Set<String>> it : seriesNamesByFolder.entrySet()) {
					if (it.getValue().contains(name)) {
						foldersForSeries.add(it.getKey());
					}
				}
				foldersBySeriesName.put(name, foldersForSeries);
			}
		}

		// join both sets
		Map<Set<File>, Set<String>> batchSets = new HashMap<Set<File>, Set<String>>();

		while (seriesNamesByFolder.size() > 0) {
			Set<String> combinedNameSet = new TreeSet<String>(getLenientCollator(locale));
			Set<File> combinedFolderSet = new HashSet<File>();

			// build combined match set
			combinedFolderSet.add(seriesNamesByFolder.keySet().iterator().next());

			boolean resolveFurther = true;
			while (resolveFurther) {
				boolean modified = false;
				for (File folder : combinedFolderSet) {
					modified |= combinedNameSet.addAll(seriesNamesByFolder.get(folder));
				}
				for (String name : combinedNameSet) {
					modified |= combinedFolderSet.addAll(foldersBySeriesName.get(name));
				}
				resolveFurther &= modified;
			}

			// build result entry
			Set<File> combinedFileSet = new TreeSet<File>();
			for (File folder : combinedFolderSet) {
				combinedFileSet.addAll(filesByFolder.get(folder));
			}

			if (combinedFileSet.size() > 0) {
				// divide file set per complete series set
				Map<Object, List<File>> filesByEpisode = new LinkedHashMap<Object, List<File>>();
				for (File file : combinedFileSet) {
					Object eid = getEpisodeIdentifier(file.getName(), true);

					// SPECIAL CASE: 101, 201, 202, etc 3-digit SxE pattern
					if (eid == null) {
						List<SxE> d3sxe = new SeasonEpisodePattern(null, "(?<!\\p{Alnum})(\\d)(\\d{2})(?!\\p{Alnum})").match(file.getName());
						if (d3sxe != null && d3sxe.size() > 0) {
							eid = d3sxe;
						}
					}

					// merge specials into first SxE group
					if (eid == null) {
						eid = file; // open new SxE group for each unrecognized file
					}

					List<File> episodeFiles = filesByEpisode.get(eid);
					if (episodeFiles == null) {
						episodeFiles = new ArrayList<File>();
						filesByEpisode.put(eid, episodeFiles);
					}
					episodeFiles.add(file);
				}

				for (int i = 0; true; i++) {
					Set<File> series = new LinkedHashSet<File>();
					for (List<File> episode : filesByEpisode.values()) {
						if (i < episode.size()) {
							series.add(episode.get(i));
						}
					}

					if (series.isEmpty()) {
						break;
					}

					combinedFileSet.removeAll(series);
					batchSets.put(series, combinedNameSet);
				}

				if (combinedFileSet.size() > 0) {
					batchSets.put(combinedFileSet, combinedNameSet);
				}
			}

			// set folders as accounted for
			seriesNamesByFolder.keySet().removeAll(combinedFolderSet);
		}

		// handle files that have not been matched to a batch set yet
		Set<File> remainingFiles = new HashSet<File>(files);
		for (Set<File> batch : batchSets.keySet()) {
			remainingFiles.removeAll(batch);
		}
		if (remainingFiles.size() > 0) {
			batchSets.put(remainingFiles, null);
		}

		return batchSets;
	}

	public static Object getEpisodeIdentifier(CharSequence name, boolean strict) {
		// check SxE first
		Object match = getSeasonEpisodeMatcher(true).match(name);

		// then Date pattern
		if (match == null) {
			match = getDateMatcher().match(name);
		}

		// check SxE non-strict
		if (match == null && !strict) {
			match = getSeasonEpisodeMatcher(false).match(name);
		}

		return match;
	}

	public static List<String> detectSeriesNames(Collection<File> files, boolean anime, Locale locale) throws Exception {
		return detectSeriesNames(files, anime ? getAnimeIndex() : getSeriesIndex(), locale);
	}

	public static List<String> detectSeriesNames(Collection<File> files, List<IndexEntry<SearchResult>> index, Locale locale) throws Exception {
		// known series names
		List<String> unids = new ArrayList<String>();

		// try xattr metadata if enabled
		for (File it : files) {
			Object metaObject = xattr.getMetaInfo(it);
			if (metaObject instanceof Episode) {
				unids.add(((Episode) metaObject).getSeriesName());
			}
		}

		// completely trust xattr metadata if all files are tagged
		if (unids.size() == files.size()) {
			return getUniqueQuerySet(unids);
		}

		// try to detect series name via nfo files
		try {
			for (SearchResult it : lookupSeriesNameByInfoFile(files, locale)) {
				unids.add(it.getName());
			}
		} catch (Exception e) {
			debug.warning("Failed to lookup info by id: " + e);
		}

		// try to detect series name via known patterns
		unids.addAll(matchSeriesMappings(files));

		// strict series name matcher for recognizing 1x01 patterns
		SeriesNameMatcher strictSeriesNameMatcher = getSeriesNameMatcher(true);

		// cross-reference known series names against file structure
		try {
			Set<String> folders = new LinkedHashSet<String>();
			Set<String> filenames = new LinkedHashSet<String>();
			for (File f : files) {
				for (int i = 0; i < 3 && f != null && !isStructureRoot(f); i++, f = f.getParentFile()) {
					String fn = getName(f);

					// try to minimize noise
					String sn = strictSeriesNameMatcher.matchByEpisodeIdentifier(fn);
					if (sn != null) {
						fn = sn;
					}

					(i == 0 ? filenames : folders).add(fn); // keep series name unique with year
				}
			}

			// check foldernames first
			List<String> matches = matchSeriesByName(folders, 0, index);

			// check all filenames if necessary
			if (matches.isEmpty()) {
				matches.addAll(matchSeriesByName(filenames, 0, index));
				matches.addAll(matchSeriesByName(stripReleaseInfo(filenames, false), 0, index));
			}

			// use lenient sub sequence matching only as fallback and try name without spacing logic that may mess up any lookup
			if (matches.isEmpty()) {
				// try to narrow down file to series name as best as possible
				List<String> sns = new ArrayList<String>();
				sns.addAll(folders);
				sns.addAll(filenames);
				for (int i = 0; i < sns.size(); i++) {
					String sn = strictSeriesNameMatcher.matchByEpisodeIdentifier(sns.get(i));
					if (sn != null) {
						sns.set(i, sn);
					}
				}
				for (SearchResult it : matchSeriesFromStringWithoutSpacing(stripReleaseInfo(sns, false), true, index)) {
					matches.add(it.getName());
				}

				matches.addAll(matchSeriesByName(folders, 2, index));
				matches.addAll(matchSeriesByName(filenames, 2, index));

				// pass along only valid terms
				unids.addAll(stripBlacklistedTerms(matches));
			} else {
				// trust terms matched by 0-stance
				unids.addAll(matches);
			}
		} catch (Exception e) {
			debug.warning("Failed to match folder structure: " + e);
		}

		// match common word sequence and clean detected word sequence from unwanted elements
		Set<String> queries = new LinkedHashSet<String>();

		// check for known pattern matches
		for (boolean strict : new boolean[] { true, false }) {
			if (queries.isEmpty()) {
				// check CWS matches
				SeriesNameMatcher seriesNameMatcher = getSeriesNameMatcher(strict);
				queries.addAll(strictSeriesNameMatcher.matchAll(files.toArray(new File[files.size()])));

				// try before SxE pattern
				if (queries.isEmpty()) {
					for (File f : files) {
						for (File path : listPathTail(f, 2, true)) {
							String fn = getName(path);
							// ignore non-strict series name parsing if there are movie year patterns
							if (!strict && parseMovieYear(fn).size() > 0) {
								break;
							}
							String sn = seriesNameMatcher.matchByEpisodeIdentifier(fn);
							if (sn != null && sn.length() > 0) {
								// try simplification by separator (for name - title naming style)
								if (!strict) {
									String sn2 = seriesNameMatcher.matchBySeparator(fn);
									if (sn2 != null && sn2.length() > 0) {
										if (sn2.length() < sn.length()) {
											sn = sn2;
										}
									}
								}
								queries.add(sn);
								break;
							}

							// account for {n}/{s00e00} folder structure (e.g. Firefly/S01E01 - Pilot)
							if (sn == null && path.isDirectory()) {
								queries.addAll(stripBlacklistedTerms(stripReleaseInfo(singleton(path.getName()), true)));
							}
						}
					}
				}
			}
		}

		debug.finest(format("Match Series Name => %s %s", unids, queries));

		List<String> querySet = getUniqueQuerySet(unids, queries);
		debug.finest(format("Query Series => %s", querySet));
		return querySet;
	}

	public static List<String> matchSeriesMappings(Collection<File> files) {
		try {
			Map<Pattern, String> patterns = releaseInfo.getSeriesMappings();
			List<String> matches = new ArrayList<String>();
			for (File file : files) {
				patterns.forEach((pattern, seriesName) -> {
					if (pattern.matcher(getName(file)).find()) {
						matches.add(seriesName);
					}
				});
			}
			return matches;
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to load series mappings: " + e.getMessage(), e);
		}
		return emptyList();
	}

	private static final ArrayList<IndexEntry<SearchResult>> seriesIndex = new ArrayList<IndexEntry<SearchResult>>();

	public static List<IndexEntry<SearchResult>> getSeriesIndex() throws IOException {
		return getIndex(() -> {
			try {
				return releaseInfo.getTheTVDBIndex();
			} catch (Exception e) {
				debug.severe("Failed to load series index: " + e.getMessage());
				return new SearchResult[0];
			}
		}, HighPerformanceMatcher::prepare, seriesIndex);
	}

	private static final ArrayList<IndexEntry<SearchResult>> animeIndex = new ArrayList<IndexEntry<SearchResult>>();

	public static List<IndexEntry<SearchResult>> getAnimeIndex() {
		return getIndex(() -> {
			try {
				return releaseInfo.getAnidbIndex();
			} catch (Exception e) {
				debug.severe("Failed to load anime index: " + e.getMessage());
				return new SearchResult[0];
			}
		}, HighPerformanceMatcher::prepare, animeIndex);
	}

	public static List<String> matchSeriesByName(Collection<String> files, int maxStartIndex, List<IndexEntry<SearchResult>> index) throws Exception {
		HighPerformanceMatcher nameMatcher = new HighPerformanceMatcher(maxStartIndex);
		List<String> matches = new ArrayList<String>();

		for (CollationKey[] name : HighPerformanceMatcher.prepare(files)) {
			IndexEntry<SearchResult> bestMatch = null;
			for (IndexEntry<SearchResult> it : index) {
				CollationKey[] commonName = nameMatcher.matchFirstCommonSequence(new CollationKey[][] { name, it.getLenientKey() });
				if (commonName != null && commonName.length >= it.getLenientKey().length && (bestMatch == null || commonName.length > bestMatch.getLenientKey().length)) {
					bestMatch = it;
				}
			}
			if (bestMatch != null) {
				matches.add(bestMatch.getLenientName());
			}
		}

		// sort by length of name match (descending)
		return matches.stream().sorted((a, b) -> {
			return Integer.compare(b.length(), a.length());
		}).collect(toList());
	}

	public static List<SearchResult> matchSeriesFromStringWithoutSpacing(Collection<String> names, boolean strict, List<IndexEntry<SearchResult>> index) throws IOException {
		// clear name of punctuation, spacing, and leading 'The' or 'A' that are common causes for word-lookup to fail
		Pattern spacing = Pattern.compile("(^(?i)(The|A)\\b)|[\\p{Punct}\\p{Space}]+");

		List<String> terms = new ArrayList<String>(names.size());
		for (String it : names) {
			String term = spacing.matcher(it).replaceAll("").toLowerCase();
			if (term.length() >= 3) {
				terms.add(term); // only consider words, not just random letters
			}
		}

		// similarity threshold based on strict/non-strict
		SimilarityMetric metric = new NameSimilarityMetric();
		float similarityThreshold = strict ? 0.75f : 0.5f;

		List<SearchResult> seriesList = new ArrayList<SearchResult>();
		for (IndexEntry<SearchResult> it : index) {
			String name = spacing.matcher(it.getLenientName()).replaceAll("").toLowerCase();
			for (String term : terms) {
				if (term.contains(name)) {
					if (metric.getSimilarity(term, name) >= similarityThreshold) {
						seriesList.add(it.getObject());
					}
					break;
				}
			}
		}
		return seriesList;
	}

	public static List<Movie> detectMovie(File movieFile, MovieIdentificationService service, Locale locale, boolean strict) throws Exception {
		List<Movie> options = new ArrayList<Movie>();

		// try xattr metadata if enabled
		if (movieFile.exists()) {
			Object metaObject = xattr.getMetaInfo(movieFile);
			if (metaObject instanceof Movie) {
				options.add((Movie) metaObject);
			}
		}

		// lookup by id from nfo file
		if (service != null) {
			for (int imdbid : grepImdbId(movieFile.getPath())) {
				Movie movie = service.getMovieDescriptor(new Movie(imdbid), locale);
				if (movie != null) {
					options.add(movie);
				}
			}

			// try to grep imdb id from nfo files
			try {
				for (int imdbid : grepImdbIdFor(movieFile)) {
					Movie movie = service.getMovieDescriptor(new Movie(imdbid), locale);
					if (movie != null) {
						options.add(movie);
					}
				}
			} catch (Exception e) {
				debug.warning("Failed to lookup info by id: " + e);
			}
		}

		// search by file name or folder name (NOTE: can't initialize with known options because misleading NFO files may lead to bad matches)
		List<String> names = new ArrayList<String>(2);

		// 1. term: try to match movie pattern 'name (year)' or use filename as is
		names.add(getName(movieFile));

		// 2. term: first meaningful parent folder
		File movieFolder = guessMovieFolder(movieFile);
		if (movieFolder != null) {
			names.add(getName(movieFolder));
		}

		// reduce movie names
		Set<String> terms = reduceMovieNamePermutations(names);
		List<Movie> movieNameMatches = matchMovieName(terms, true, 0);

		// skip further queries if collected matches are already sufficient
		if (movieNameMatches.size() > 0) {
			options.addAll(movieNameMatches);
			return sortMoviesBySimilarity(options, terms);
		}

		if (movieNameMatches.isEmpty()) {
			movieNameMatches = matchMovieName(terms, strict, 2);
		}

		// skip further queries if collected matches are already sufficient
		if (options.size() > 0 && movieNameMatches.size() > 0) {
			options.addAll(movieNameMatches);
			return sortMoviesBySimilarity(options, terms);
		}

		// if matching name+year failed, try matching only by name (in non-strict mode we would have checked these cases already by now)
		if (movieNameMatches.isEmpty() && strict) {
			movieNameMatches = matchMovieName(terms, false, 0);
			if (movieNameMatches.isEmpty()) {
				movieNameMatches = matchMovieName(terms, false, 2);
			}
		}

		// assume name without spacing will mess up any lookup
		if (movieNameMatches.isEmpty()) {
			movieNameMatches = matchMovieFromStringWithoutSpacing(terms, strict);

			// check alternative terms if necessary and only if they're different
			if (movieNameMatches.isEmpty()) {
				List<String> alternativeTerms = stripReleaseInfo(terms, true);
				if (!terms.containsAll(alternativeTerms)) {
					movieNameMatches = matchMovieFromStringWithoutSpacing(alternativeTerms, strict);
				}
			}
		}

		// query by file / folder name
		if (service != null) {
			List<Movie> results = queryMovieByFileName(terms, service, locale);

			// try query without year as it sometimes messes up results if years don't match properly (movie release years vs dvd release year, etc)
			if (results.isEmpty() && !strict) {
				List<String> lastResortQueryList = new ArrayList<String>();
				Pattern yearPattern = Pattern.compile("(?:19|20)\\d{2}");
				Pattern akaPattern = Pattern.compile("\\bAKA\\b", Pattern.CASE_INSENSITIVE);
				for (String term : terms) {
					if (yearPattern.matcher(term).find() || akaPattern.matcher(term).find()) {
						// try to separate AKA titles as well into separate searches
						for (String mn : akaPattern.split(yearPattern.matcher(term).replaceAll(""))) {
							lastResortQueryList.add(mn.trim());
						}
					}
				}
				if (lastResortQueryList.size() > 0) {
					results = queryMovieByFileName(lastResortQueryList, service, locale);
				}
			}

			// online results have better ranking so add them first
			options.addAll(results);
		}

		// consider potential local index matches second
		options.addAll(movieNameMatches);

		// sort by relevance
		return sortMoviesBySimilarity(options, terms);
	}

	public static List<Movie> detectMovieWithYear(File movieFile, MovieIdentificationService service, Locale locale, boolean strict) throws Exception {
		// in non-strict mode, process all movie files as best as possible
		if (!strict) {
			return detectMovie(movieFile, service, locale, strict);
		}

		// in strict mode, only process movies that follow the name (year) pattern, so we can confirm each match by checking the movie year
		List<Integer> year = parseMovieYear(getRelativePathTail(movieFile, 3).getPath());
		if (year.isEmpty() || isEpisode(movieFile, true)) {
			return null;
		}

		// allow only movie matches where the the movie year matches the year pattern in the filename
		return detectMovie(movieFile, service, locale, strict).stream().filter(m -> year.contains(m.getYear())).collect(toList());
	}

	public static SimilarityMetric getMovieMatchMetric() {
		return new MetricAvg(new NameSimilarityMetric(), new StringEqualsMetric() {

			@Override
			protected String normalize(Object object) {
				return super.normalize(removeTrailingBrackets(object.toString()));
			}
		}, new NumericSimilarityMetric() {

			private Pattern year = Pattern.compile("\\b\\d{4}\\b");

			@Override
			protected String normalize(Object object) {
				return streamMatches(object.toString(), year).mapToInt(Integer::parseInt).flatMap(i -> IntStream.of(i, i + 1)).mapToObj(Objects::toString).collect(joining(" "));
			}

			@Override
			public float getSimilarity(Object o1, Object o2) {
				return super.getSimilarity(o1, o2) * 2; // extra weight for year match
			}
		}, new SequenceMatchSimilarity(), new SequenceMatchSimilarity(0, true));
	}

	public static Movie getLocalizedMovie(MovieIdentificationService service, Movie movie, Locale locale) throws Exception {
		// retrieve language and service specific movie object
		if (movie != null) {
			try {
				return service.getMovieDescriptor(movie, locale);
			} catch (Exception e) {
				debug.log(Level.WARNING, "Failed to retrieve localized movie data", e);
			}
		}
		return null;
	}

	public static SimilarityMetric getSeriesMatchMetric() {
		return new MetricAvg(new SequenceMatchSimilarity(), new NameSimilarityMetric(), new SequenceMatchSimilarity(0, true));
	}

	public static <T extends SearchResult> List<T> sortBySimilarity(Collection<T> options, Collection<String> terms, SimilarityMetric metric) {
		return sortBySimilarity(options, terms, metric, SearchResult::getEffectiveNames);
	}

	public static <T extends SearchResult> List<T> sortBySimilarity(Collection<T> options, Collection<String> terms, SimilarityMetric metric, Function<SearchResult, Collection<String>> mapper) {
		// similarity comparator with multi-value support
		SimilarityComparator<SearchResult, String> comparator = new SimilarityComparator<SearchResult, String>(metric, terms, mapper);

		// sort by ranking and remove duplicate entries
		List<T> ranking = options.stream().sorted(comparator).distinct().collect(toList());

		// DEBUG
		debug.finest(format("Rank %s => %s", terms, ranking));

		// sort by ranking and remove duplicate entries
		return ranking;
	}

	public static List<Movie> sortMoviesBySimilarity(Collection<Movie> options, Collection<String> terms) throws Exception {
		Set<String> paragon = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		paragon.addAll(stripReleaseInfo(terms, true));
		paragon.addAll(stripReleaseInfo(terms, false));

		return sortBySimilarity(options, paragon, getMovieMatchMetric());
	}

	public static boolean isEpisodeNumberMatch(File f, Episode e) {
		float similarity = EpisodeMetrics.EpisodeIdentifier.getSimilarity(f, e);
		if (similarity >= 1) {
			return true;
		} else if (similarity >= 0.5 && e.getSeason() == null && e.getEpisode() != null && e.getSpecial() == null) {
			List<SxE> numbers = parseEpisodeNumber(f, false);
			return numbers != null && numbers.stream().anyMatch(it -> it.season < 0 && it.episode == e.getEpisode());
		}
		return false;
	}

	public static List<Integer> parseMovieYear(String name) {
		return matchIntegers(name).stream().filter(DateMatcher.DEFAULT_SANITY::acceptYear).collect(toList());
	}

	public static String reduceMovieName(String name, boolean strict) throws IOException {
		Matcher matcher = compile(strict ? "^(.+)[\\[\\(]((?:19|20)\\d{2})[\\]\\)]" : "^(.+?)((?:19|20)\\d{2})").matcher(name);
		if (matcher.find() && parseMovieYear(matcher.group(2)).size() > 0) {
			return String.format("%s %s", trimTrailingPunctuation(matcher.group(1)), matcher.group(2));
		}
		return null;
	}

	public static Set<String> reduceMovieNamePermutations(Collection<String> terms) throws IOException {
		LinkedList<String> names = new LinkedList<String>();

		for (String it : terms) {
			String rn = reduceMovieName(it, true);
			if (rn != null) {
				names.addFirst(rn);
			} else {
				names.addLast(it); // unsure, keep original term just in case, but also try non-strict reduce
				rn = reduceMovieName(it, false);
				if (rn != null) {
					names.addLast(rn);
				}
			}
		}

		return new LinkedHashSet<String>(names);
	}

	public static File guessMovieFolder(File movieFile) throws Exception {
		// special case for folder mode
		if (movieFile.isDirectory()) {
			File f = movieFile;

			// check for double nested structures
			if (!isStructureRoot(f.getParentFile()) && checkMovie(f.getParentFile(), false) != null && checkMovie(f, false) == null) {
				return f.getParentFile();
			} else {
				return isStructureRoot(f) ? null : f;
			}
		}

		// first parent folder that matches a movie (max 4 levels deep)
		for (boolean strictness : new boolean[] { true, false }) {
			File f = movieFile.getParentFile();
			for (int i = 0; f != null && i < 4 && !isStructureRoot(f); f = f.getParentFile(), i++) {
				String term = stripReleaseInfo(f.getName());
				if (term.length() > 0 && checkMovie(f, strictness) != null) {
					return f;
				}
			}
		}

		// otherwise try the first potentially meaningful parent folder (max 2 levels deep)
		File f = movieFile.getParentFile();
		for (int i = 0; f != null && i < 2 && !isStructureRoot(f); f = f.getParentFile(), i++) {
			String term = stripReleaseInfo(f.getName());
			if (term.length() > 0) {
				// check for double nested structures
				if (checkMovie(f.getParentFile(), false) != null && checkMovie(f, false) == null) {
					return f.getParentFile();
				} else {
					return f;
				}
			}
		}

		if (movieFile.getParentFile() != null && !isStructureRoot(f.getParentFile()) && stripReleaseInfo(movieFile.getParentFile().getName()).length() > 0) {
			return movieFile.getParentFile();
		}
		return null;
	}

	public static Movie checkMovie(File file, boolean strict) {
		List<Movie> matches = file == null ? null : matchMovieName(singleton(file.getName()), strict, 4);
		return matches == null || matches.isEmpty() ? null : matches.get(0);
	}

	private static final ArrayList<IndexEntry<Movie>> movieIndex = new ArrayList<IndexEntry<Movie>>();

	private static <T extends SearchResult> List<IndexEntry<T>> getIndex(Supplier<T[]> function, Function<T, List<IndexEntry<T>>> mapper, ArrayList<IndexEntry<T>> sink) {
		synchronized (sink) {
			if (sink.isEmpty()) {
				T[] index = function.get();
				sink.ensureCapacity(index.length * 4); // alias names
				stream(index).map(mapper).forEach(sink::addAll);
			}
			return sink;
		}
	}

	public static List<IndexEntry<Movie>> getMovieIndex() {
		return getIndex(() -> {
			try {
				return releaseInfo.getMovieList();
			} catch (Exception e) {
				debug.severe("Failed to load movie index: " + e.getMessage());
				return new Movie[0];
			}
		}, HighPerformanceMatcher::prepare, movieIndex);
	}

	public static List<Movie> matchMovieName(Collection<String> files, boolean strict, int maxStartIndex) {
		// cross-reference file / folder name with movie list
		final HighPerformanceMatcher nameMatcher = new HighPerformanceMatcher(maxStartIndex);
		final Map<Movie, String> matchMap = new HashMap<Movie, String>();

		List<CollationKey[]> names = HighPerformanceMatcher.prepare(files);

		for (IndexEntry<Movie> movie : getMovieIndex()) {
			for (CollationKey[] name : names) {
				CollationKey[] commonName = nameMatcher.matchFirstCommonSequence(new CollationKey[][] { name, movie.getLenientKey() });
				if (commonName != null && commonName.length >= movie.getLenientKey().length) {
					CollationKey[] strictCommonName = nameMatcher.matchFirstCommonSequence(new CollationKey[][] { name, movie.getStrictKey() });
					if (strictCommonName != null && strictCommonName.length >= movie.getStrictKey().length) {
						// prefer strict match
						matchMap.put(movie.getObject(), movie.getStrictName());
					} else if (!strict) {
						// make sure the common identifier is not just the year
						matchMap.put(movie.getObject(), movie.getLenientName());
					}
				}
			}
		}

		// sort by length of name match (descending)
		return matchMap.keySet().stream().sorted((a, b) -> {
			return Integer.compare(matchMap.get(b).length(), matchMap.get(a).length());
		}).collect(toList());
	}

	public static List<Movie> matchMovieFromStringWithoutSpacing(Collection<String> names, boolean strict) {
		// clear name of punctuation, spacing, and leading 'The' or 'A' that are common causes for word-lookup to fail
		Pattern spacing = Pattern.compile("(^(?i)(The|A)\\b)|[\\p{Punct}\\p{Space}]+");

		List<String> terms = new ArrayList<String>(names.size());
		for (String it : names) {
			String term = spacing.matcher(it).replaceAll("").toLowerCase();
			if (term.length() >= 3) {
				terms.add(term); // only consider words, not just random letters
			}
		}

		// similarity threshold based on strict/non-strict
		SimilarityMetric metric = new NameSimilarityMetric();
		float similarityThreshold = strict ? 0.9f : 0.5f;

		LinkedList<Movie> movies = new LinkedList<Movie>();
		for (IndexEntry<Movie> it : getMovieIndex()) {
			String name = spacing.matcher(it.getLenientName()).replaceAll("").toLowerCase();
			for (String term : terms) {
				if (term.contains(name)) {
					String year = String.valueOf(it.getObject().getYear());
					if (term.contains(year) && metric.getSimilarity(term, name + year) > similarityThreshold) {
						movies.addFirst(it.getObject());
					} else if (metric.getSimilarity(term, name) > similarityThreshold) {
						movies.addLast(it.getObject());
					}
					break;
				}
			}
		}
		return new ArrayList<Movie>(movies);
	}

	private static List<Movie> queryMovieByFileName(Collection<String> files, MovieIdentificationService queryLookupService, Locale locale) throws Exception {
		// remove blacklisted terms and remove duplicates
		List<String> querySet = getUniqueQuerySet(emptySet(), files);

		// DEBUG
		debug.finest(format("Query Movie => %s", querySet));

		final Map<Movie, Float> probabilityMap = new LinkedHashMap<Movie, Float>();
		final SimilarityMetric metric = getMovieMatchMetric();
		for (String query : querySet) {
			for (Movie movie : queryLookupService.searchMovie(query.toLowerCase(), locale)) {
				probabilityMap.put(movie, metric.getSimilarity(query, movie));
			}
		}

		// sort by similarity to original query (descending)
		List<Movie> results = new ArrayList<Movie>(probabilityMap.keySet());
		results.sort((a, b) -> {
			return probabilityMap.get(b).compareTo(probabilityMap.get(a));
		});

		return results;
	}

	private static List<String> getUniqueQuerySet(Collection<String> exactMatches, Collection<String>... guessMatches) {
		Map<String, String> unique = new LinkedHashMap<String, String>();

		// unique key function (case-insensitive ignore-punctuation)
		Function<String, String> normalize = (s) -> normalizePunctuation(s).toLowerCase();
		addUniqueQuerySet(exactMatches, normalize, Function.identity(), unique);

		// remove blacklisted terms and remove duplicates
		List<String> extra = stream(guessMatches).flatMap(Collection::stream).filter(t -> {
			return !unique.containsKey(normalize.apply(t));
		}).collect(toList());

		Set<String> terms = new LinkedHashSet<String>();
		terms.addAll(stripReleaseInfo(extra, true));
		terms.addAll(stripReleaseInfo(extra, false));
		addUniqueQuerySet(stripBlacklistedTerms(terms), normalize, normalize, unique);

		return new ArrayList<String>(unique.values());
	}

	private static void addUniqueQuerySet(Collection<String> terms, Function<String, String> keyFunction, Function<String, String> valueFunction, Map<String, String> uniqueMap) {
		for (String term : terms) {
			if (term != null && term.length() > 0) {
				String key = keyFunction.apply(term);
				if (key != null && key.length() > 0) {
					uniqueMap.computeIfAbsent(key, k -> valueFunction.apply(term));
				}
			}
		}
	}

	public static List<Movie> matchMovieByWordSequence(String name, Collection<Movie> options, int maxStartIndex) {
		List<Movie> movies = new ArrayList<Movie>();

		HighPerformanceMatcher nameMatcher = new HighPerformanceMatcher(maxStartIndex);
		CollationKey[] nameSeq = HighPerformanceMatcher.prepare(normalizePunctuation(name));

		for (Movie movie : options) {
			for (String alias : movie.getEffectiveNames()) {
				CollationKey[] movieSeq = HighPerformanceMatcher.prepare(normalizePunctuation(alias));
				CollationKey[] commonSeq = nameMatcher.matchFirstCommonSequence(new CollationKey[][] { nameSeq, movieSeq });

				if (commonSeq != null && commonSeq.length >= movieSeq.length) {
					movies.add(movie);
					break;
				}
			}
		}

		return movies;
	}

	private static Pattern formatInfoPattern = releaseInfo.getVideoFormatPattern(true);

	public static String stripFormatInfo(CharSequence name) {
		return formatInfoPattern.matcher(name).replaceAll("");
	}

	public static boolean isVolumeRoot(File folder) {
		return folder == null || folder.getName() == null || folder.getName().isEmpty() || releaseInfo.getVolumeRoots().contains(folder);
	}

	public static boolean isStructureRoot(File folder) throws Exception {
		return isVolumeRoot(folder) || releaseInfo.getStructureRootPattern().matcher(folder.getName()).matches() || ApplicationFolder.UserHome.get().equals(folder.getParentFile());
	}

	public static File getStructureRoot(File file) throws Exception {
		boolean structureRoot = false;
		for (File it : listPathTail(file, Integer.MAX_VALUE, true)) {
			if (structureRoot || isStructureRoot(it)) {
				if (it.isDirectory()) {
					return it;
				}
				structureRoot = true; // find first existing folder at or after the structure root folder (which may not exist yet)
			}
		}
		return null;
	}

	public static List<String> listStructurePathTail(File file) throws Exception {
		LinkedList<String> relativePath = new LinkedList<String>();
		for (File it : listPathTail(file, FILE_WALK_MAX_DEPTH, true)) {
			if (isStructureRoot(it))
				break;

			// iterate path in reverse
			relativePath.addFirst(it.getName());
		}
		return relativePath;
	}

	public static File getStructurePathTail(File file) throws Exception {
		List<String> relativePath = listStructurePathTail(file);
		return relativePath.isEmpty() ? null : new File(String.join(File.separator, relativePath));
	}

	public static Map<File, List<File>> mapByMediaFolder(Collection<File> files) {
		Map<File, List<File>> mediaFolders = new HashMap<File, List<File>>();
		for (File f : files) {
			File folder = guessMediaFolder(f);
			List<File> value = mediaFolders.get(folder);
			if (value == null) {
				value = new ArrayList<File>();
				mediaFolders.put(folder, value);
			}
			value.add(f);
		}
		return mediaFolders;
	}

	public static Map<String, List<File>> mapByMediaExtension(Iterable<File> files) {
		Map<String, List<File>> map = new LinkedHashMap<String, List<File>>();

		for (File file : files) {
			String key = getExtension(file);

			// allow extended extensions for subtitles files, for example name.eng.srt => map by en.srt
			if (key != null && SUBTITLE_FILES.accept(file)) {
				Locale locale = releaseInfo.getSubtitleLanguageTag(getName(file));
				if (locale != null) {
					key = locale.getLanguage() + '.' + key;
				}
			}

			// normalize to lower-case
			if (key != null) {
				key = key.toLowerCase();
			}

			List<File> valueList = map.get(key);
			if (valueList == null) {
				valueList = new ArrayList<File>();
				map.put(key, valueList);
			}

			valueList.add(file);
		}

		return map;
	}

	public static List<List<File>> groupByMediaCharacteristics(Collection<File> files) {
		List<List<File>> groups = new ArrayList<List<File>>();

		mapByExtension(files).forEach((extension, filesByExtension) -> {
			if (filesByExtension.size() < 2) {
				groups.add(filesByExtension);
				return;
			}

			filesByExtension.stream().collect(groupingBy(f -> {
				if (VIDEO_FILES.accept(f) && f.length() > ONE_MEGABYTE) {
					try (MediaCharacteristics mi = MediaCharacteristicsParser.open(f)) {
						ChronoUnit d = mi.getDuration().toMinutes() < 10 ? ChronoUnit.MINUTES : ChronoUnit.HOURS;
						String v = mi.getVideoCodec();
						String a = mi.getAudioCodec();
						Integer w = mi.getWidth();
						Integer h = mi.getHeight();
						return asList(d, v, a, w, h);
					} catch (Exception e) {
						debug.warning(format("Failed to read media characteristics: %s", e.getMessage()));
					}
				} else if (SUBTITLE_FILES.accept(f) && f.length() > ONE_KILOBYTE) {
					try {
						Language language = detectSubtitleLanguage(f);
						if (language != null) {
							return asList(language.getCode());
						}
					} catch (Exception e) {
						debug.warning(format("Failed to detect subtitle language: %s", e.getMessage()));
					}
				}

				// default to grouping by most likely media folder
				return Optional.ofNullable(guessMediaFolder(f)).map(File::getName);
			}, LinkedHashMap::new, toList())).forEach((group, videos) -> groups.add(videos));
		});

		return groups;
	}

	public static Map<String, List<File>> mapBySeriesName(Collection<File> files, boolean anime, Locale locale) throws Exception {
		Map<String, List<File>> result = new TreeMap<String, List<File>>(String.CASE_INSENSITIVE_ORDER);

		for (File f : files) {
			List<String> names = detectSeriesNames(singleton(f), anime, locale);
			String key = names.isEmpty() ? "" : names.get(0);

			List<File> value = result.get(key);
			if (value == null) {
				value = new ArrayList<File>();
				result.put(key, value);
			}
			value.add(f);
		}

		return result;
	}

	public static Movie matchMovie(File file, int depth) {
		List<String> names = new ArrayList<String>(depth);
		for (File it : listPathTail(file, depth, true)) {
			names.add(it.getName());
		}

		List<Movie> matches = matchMovieName(names, true, 0);
		return matches.size() > 0 ? matches.get(0) : null;
	}

	public static File guessMediaFolder(File file) {
		List<File> tail = listPathTail(file, 3, true);

		// skip file itself (first entry)
		for (int i = 1; i < tail.size(); i++) {
			File folder = tail.get(i);
			String term = stripReleaseInfo(folder.getName());
			if (term.length() > 0) {
				return folder;
			}
		}

		// simply default to parent folder
		return file.getParentFile();
	}

	public static List<String> stripReleaseInfo(Collection<String> names, boolean strict) {
		try {
			return releaseInfo.cleanRelease(names, strict);
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to strip release info: " + e.getMessage(), e);
		}
		return new ArrayList<String>(names);
	}

	public static String stripReleaseInfo(String name, boolean strict) {
		Iterator<String> value = stripReleaseInfo(singleton(name), strict).iterator();
		if (value.hasNext()) {
			return value.next();
		}
		return ""; // default value in case all tokens are stripped away
	}

	public static String stripReleaseInfo(String name) {
		return stripReleaseInfo(name, true);
	}

	private static final Resource<Pattern> blacklistPattern = Resource.lazy(releaseInfo::getBlacklistPattern);

	public static List<String> stripBlacklistedTerms(Collection<String> names) {
		try {
			Pattern pattern = blacklistPattern.get();
			return names.stream().filter(s -> pattern.matcher(s).replaceAll("").trim().length() > 0).collect(toList());
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to strip release info: " + e.getMessage(), e);
		}
		return emptyList();
	}

	public static Set<Integer> grepImdbIdFor(File file) throws Exception {
		Set<Integer> collection = new LinkedHashSet<Integer>();
		List<File> nfoFiles = new ArrayList<File>();

		if (file.isDirectory()) {
			nfoFiles.addAll(listFiles(file, NFO_FILES));
		} else if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
			nfoFiles.addAll(getChildren(file.getParentFile(), NFO_FILES));
		}

		// parse IMDb IDs from NFO files
		for (File nfo : nfoFiles) {
			collection.addAll(grepImdbId(readTextFile(nfo)));
		}

		return collection;
	}

	public static Set<SearchResult> lookupSeriesNameByInfoFile(Collection<File> files, Locale language) throws Exception {
		Set<SearchResult> names = new LinkedHashSet<SearchResult>();

		SortedSet<File> folders = new TreeSet<File>(reverseOrder());
		for (File f : files) {
			for (int i = 0; i < 2 && f.getParentFile() != null; i++) {
				f = f.getParentFile();
				folders.add(f);
			}
		}

		// search for id in sibling nfo files
		for (File folder : folders) {
			if (!folder.exists()) {
				continue;
			}

			for (File nfo : getChildren(folder, NFO_FILES)) {
				String text = readTextFile(nfo);

				for (int imdbid : grepImdbId(text)) {
					SearchResult series = WebServices.TheTVDB.lookupByIMDbID(imdbid, language);
					if (series != null) {
						names.add(series);
					}
				}

				for (int tvdbid : grepTheTvdbId(text)) {
					SearchResult series = WebServices.TheTVDB.lookupByID(tvdbid, language);
					if (series != null) {
						names.add(series);
					}
				}
			}
		}

		return names;
	}

	public static List<Integer> grepImdbId(CharSequence text) {
		// scan for imdb id patterns like tt1234567
		Pattern imdbId = Pattern.compile("(?<!\\p{Alnum})tt(\\d{7})(?!\\p{Alnum})", Pattern.CASE_INSENSITIVE);
		return streamMatches(text, imdbId, m -> m.group(1)).map(Integer::parseInt).collect(toList());
	}

	public static List<Integer> grepTheTvdbId(CharSequence text) {
		// scan for thetvdb id patterns like http://www.thetvdb.com/?tab=series&id=78874&lid=14
		Pattern tvdbUrl = Pattern.compile("thetvdb.com[\\p{Graph}]*?[\\p{Punct}]id=(\\d+)", Pattern.CASE_INSENSITIVE);
		return streamMatches(text, tvdbUrl, m -> m.group(1)).map(Integer::parseInt).collect(toList());
	}

	public static Movie grepMovie(File nfo, MovieIdentificationService resolver, Locale locale) throws Exception {
		List<Integer> imdbId = grepImdbId(readTextFile(nfo));
		return imdbId.isEmpty() ? null : resolver.getMovieDescriptor(new Movie(imdbId.get(0)), locale);
	}

	public static SeriesInfo grepSeries(File nfo, Locale locale) throws Exception {
		List<Integer> tvdbId = grepTheTvdbId(readTextFile(nfo));
		return tvdbId.isEmpty() ? null : WebServices.TheTVDB.getSeriesInfo(tvdbId.get(0), locale);
	}

	public static <T extends SearchResult> List<T> getProbableMatches(String query, Collection<T> options, boolean alias, boolean strict) {
		if (query == null) {
			return options.stream().distinct().collect(toList());
		}

		// check all alias names, or just the primary name
		Function<SearchResult, Collection<String>> names = alias ? SearchResult::getEffectiveNames : f -> singleton(f.getName());

		// auto-select most probable search result
		List<T> probableMatches = new ArrayList<T>();

		// use name similarity metric
		SimilarityMetric metric = new NameSimilarityMetric();
		float threshold = strict && options.size() > 1 ? 0.8f : 0.6f;
		float sanity = strict && options.size() > 1 ? 0.5f : 0.2f;

		// remove trailing braces, e.g. Doctor Who (2005) -> doctor who
		String q = removeTrailingBrackets(query).toLowerCase();

		// find probable matches using name similarity > 0.8 (or > 0.6 in non-strict mode)
		for (T option : options) {
			float f = 0;
			for (String n : names.apply(option)) {
				n = removeTrailingBrackets(n).toLowerCase();
				f = Math.max(f, metric.getSimilarity(q, n));

				// boost matching beginnings
				if (f >= sanity && n.startsWith(q)) {
					f = 1;
					break;
				}
			}

			if (f >= threshold) {
				probableMatches.add(option);
			}
		}

		return sortBySimilarity(probableMatches, singleton(query), new NameSimilarityMetric(), names);
	}

	public static void warmupCachedResources() throws Exception {
		// load filter data
		MediaDetection.getClutterFileFilter();
		MediaDetection.getDiskFolderFilter();
		MediaDetection.matchSeriesMappings(emptyList());

		// load movie/series index
		MediaDetection.stripReleaseInfo(singleton(""), true);
		MediaDetection.matchSeriesByName(singleton(""), -1, MediaDetection.getSeriesIndex());
		MediaDetection.matchSeriesByName(singleton(""), -1, MediaDetection.getAnimeIndex());
		MediaDetection.matchMovieName(singleton(""), true, -1);
	}

}
