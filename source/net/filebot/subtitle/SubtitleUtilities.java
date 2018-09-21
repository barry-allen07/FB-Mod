package net.filebot.subtitle;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.BuiltInLanguages;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;

import net.filebot.Language;
import net.filebot.similarity.EpisodeMetrics;
import net.filebot.similarity.Match;
import net.filebot.similarity.Matcher;
import net.filebot.similarity.MetricAvg;
import net.filebot.similarity.NameSimilarityMetric;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.similarity.SequenceMatchSimilarity;
import net.filebot.similarity.SimilarityComparator;
import net.filebot.similarity.SimilarityMetric;
import net.filebot.util.ByteBufferInputStream;
import net.filebot.util.ByteBufferOutputStream;
import net.filebot.vfs.ArchiveType;
import net.filebot.vfs.MemoryFile;
import net.filebot.web.Movie;
import net.filebot.web.SubtitleDescriptor;
import net.filebot.web.SubtitleProvider;
import net.filebot.web.SubtitleSearchResult;
import net.filebot.web.VideoHashSubtitleService;

public final class SubtitleUtilities {

	public static Map<File, List<SubtitleDescriptor>> lookupSubtitlesByHash(VideoHashSubtitleService service, Collection<File> files, Locale locale, boolean addOptions, boolean strict) throws Exception {
		Map<File, List<SubtitleDescriptor>> options = service.getSubtitleList(files.toArray(new File[files.size()]), locale);
		Map<File, List<SubtitleDescriptor>> results = new LinkedHashMap<File, List<SubtitleDescriptor>>(options.size());

		options.forEach((k, v) -> {
			// guess best hash match (default order is open bad due to invalid hash links)
			SubtitleDescriptor bestMatch = getBestMatch(k, v, strict);

			// ignore results if there is no best match
			if (bestMatch != null) {
				if (addOptions) {
					Stream<SubtitleDescriptor> top1 = Stream.of(bestMatch);
					Stream<SubtitleDescriptor> topN = v.stream().filter(Predicate.isEqual(bestMatch).negate()).sorted(SimilarityComparator.compareTo(getName(k), SubtitleDescriptor::getName));
					results.put(k, Stream.concat(top1, topN).collect(toList()));
				} else {
					results.put(k, singletonList(bestMatch));
				}
			}
		});

		return results;
	}

	public static Map<File, List<SubtitleDescriptor>> findSubtitlesByName(SubtitleProvider service, Collection<File> fileSet, Locale locale, String forceQuery, boolean addOptions, boolean strict) throws Exception {
		// ignore anything that is not a video
		fileSet = filter(fileSet, VIDEO_FILES);

		// ignore clutter files from processing
		fileSet = filter(fileSet, not(getClutterFileFilter()));

		// collect results
		Map<File, List<SubtitleDescriptor>> subtitlesByFile = new HashMap<File, List<SubtitleDescriptor>>();

		for (List<File> byMediaFolder : mapByMediaFolder(fileSet).values()) {
			for (Entry<String, List<File>> bySeries : mapBySeriesName(byMediaFolder, false, Locale.ENGLISH).entrySet()) {
				// allow early abort
				if (Thread.interrupted())
					throw new InterruptedException();

				// auto-detect query and search for subtitles
				Collection<SubtitleSearchResult> selection = new LinkedHashSet<SubtitleSearchResult>();
				Collection<String> querySet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
				List<File> files = bySeries.getValue();

				// try to guess what type of search might be required (minimize false negatives)
				boolean searchBySeries = files.stream().anyMatch(f -> isEpisode(getName(f), true) || (isEpisode(getName(f), false) && matchMovie(f, 2) == null));
				boolean searchByMovie = files.stream().anyMatch(f -> !isEpisode(getName(f), true));

				if (forceQuery != null && forceQuery.length() > 0) {
					querySet.add(forceQuery);
					searchByMovie = true; // manual query could be a movie
					searchBySeries = true; // manual query could be a tv series
				} else if (searchBySeries && bySeries.getKey().length() > 0) {
					// use auto-detected series name as query
					querySet.add(bySeries.getKey());
				} else if (searchBySeries || searchByMovie) {
					// remainder is most likely a movie, or a badly named tv series
					for (File f : files) {
						List<String> queries = new ArrayList<String>();

						// might be a movie, auto-detect movie names
						if (!isEpisode(f.getPath(), true)) {
							for (Movie it : detectMovie(f, null, Locale.ENGLISH, strict)) {
								queries.add(it.getName());
							}
						}

						if (queries.size() > 0) {
							querySet.addAll(queries);
						} else {
							// just use heavily stripped file names
							String keywords = stripReleaseInfo(getName(f), false);
							if (keywords != null && keywords.length() > 0) {
								querySet.add(keywords);
							}
						}
					}
				}

				if (searchByMovie || searchBySeries) {
					selection.addAll(findProbableSearchResults(service, querySet, searchByMovie, searchBySeries));
				}

				// try OpenSubtitles guess function if we can't make sense of the files using local search
				if (selection.isEmpty()) {
					for (File f : files) {
						try {
							selection.addAll(service.guess(getName(f)));
						} catch (Exception e) {
							debug.warning(format("Failed to identify file [%s]: %s", f.getName(), e.getMessage()));
						}
					}
				}

				if (selection.isEmpty()) {
					continue;
				}

				// search for subtitles online using the auto-detected or forced query information
				Set<SubtitleDescriptor> subtitles = new LinkedHashSet<SubtitleDescriptor>();

				// fetch subtitles for all search results
				for (SubtitleSearchResult it : selection) {
					int[][] episodeFilter = null; // no filter

					if (searchBySeries) {
						// search for subtitles for the given files
						List<SxE> numbers = files.stream().map(f -> parseEpisodeNumber(f, true)).filter(Objects::nonNull).flatMap(Collection::stream).distinct().collect(toList());

						if (numbers.size() == 1) {
							episodeFilter = numbers.stream().map(sxe -> new int[] { sxe.season, sxe.episode }).toArray(int[][]::new); // season-and-episode filter
						} else {
							episodeFilter = numbers.stream().map(sxe -> new int[] { sxe.season, -1 }).toArray(int[][]::new); // season-only filter
						}
					}

					subtitles.addAll(service.getSubtitleList(it, episodeFilter, locale));
				}

				// allow early abort
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				// files by possible subtitles matches
				for (File file : files) {
					subtitlesByFile.put(file, new ArrayList<SubtitleDescriptor>());
				}

				// add other possible matches to the options
				SimilarityMetric sanity = SubtitleMetrics.verificationMetric();
				float minMatchSimilarity = strict ? 0.9f : 0.6f;

				// first match everything as best as possible, then filter possibly bad matches
				for (Entry<File, SubtitleDescriptor> it : matchSubtitles(files, subtitles).entrySet()) {
					if (sanity.getSimilarity(it.getKey(), it.getValue()) >= minMatchSimilarity) {
						subtitlesByFile.get(it.getKey()).add(it.getValue());
					}
				}

				// this could be very slow, lets hope at this point there is not much left due to positive hash matches
				for (File file : files) {
					// add matching subtitles
					for (SubtitleDescriptor it : subtitles) {
						// grab only the first best option unless we really want all options
						if (!addOptions && subtitlesByFile.get(file).size() >= 1)
							continue;

						// ignore if it's already been added
						if (subtitlesByFile.get(file).contains(it))
							continue;

						// ignore if we're sure that SxE is a negative match
						if ((isEpisode(it.getName(), true) || isEpisode(file.getPath(), true)) && EpisodeMetrics.EpisodeIdentifier.getSimilarity(file, it) < 1)
							continue;

						// ignore if it's not similar enough
						if (sanity.getSimilarity(file, it) < minMatchSimilarity)
							continue;

						subtitlesByFile.get(file).add(it);
					}
				}
			}
		}

		return subtitlesByFile;
	}

	public static Map<File, SubtitleDescriptor> matchSubtitles(Collection<File> files, Collection<SubtitleDescriptor> subtitles) throws InterruptedException {
		Map<File, SubtitleDescriptor> subtitleByVideo = new LinkedHashMap<File, SubtitleDescriptor>();

		// optimize for generic media <-> subtitle matching
		SimilarityMetric[] metrics = SubtitleMetrics.defaultSequence();

		// first match everything as best as possible, then filter possibly bad matches
		Matcher<File, SubtitleDescriptor> matcher = new Matcher<File, SubtitleDescriptor>(files, subtitles, false, metrics);

		for (Match<File, SubtitleDescriptor> it : matcher.match()) {
			subtitleByVideo.put(it.getValue(), it.getCandidate());
		}

		return subtitleByVideo;
	}

	protected static List<SubtitleSearchResult> findProbableSearchResults(SubtitleProvider service, Collection<String> querySet, boolean searchByMovie, boolean searchBySeries) throws Exception {
		// search for and automatically select movie / show entry
		List<SubtitleSearchResult> resultSet = new ArrayList<SubtitleSearchResult>();

		for (String query : querySet) {
			// search and filter by movie/series as required
			Stream<SubtitleSearchResult> searchResults = service.search(query).stream().filter((it) -> {
				return (searchByMovie && it.isMovie()) || (searchBySeries && it.isSeries());
			});

			resultSet.addAll(filterProbableSearchResults(query, searchResults::iterator, querySet.size() == 1 ? 4 : 2));
		}
		return resultSet;
	}

	protected static List<SubtitleSearchResult> filterProbableSearchResults(String query, Iterable<SubtitleSearchResult> searchResults, int limit) {
		// auto-select most probable search result
		List<SubtitleSearchResult> probableMatches = new ArrayList<SubtitleSearchResult>();

		// use name similarity metric
		SimilarityMetric metric = new MetricAvg(new SequenceMatchSimilarity(), new NameSimilarityMetric());

		// find probable matches using name similarity > threshold
		for (SubtitleSearchResult result : searchResults) {
			if (probableMatches.size() <= limit) {
				for (String name : result.getEffectiveNames()) {
					if (metric.getSimilarity(query, removeTrailingBrackets(name)) > 0.8f || name.toLowerCase().startsWith(query.toLowerCase())) {
						probableMatches.add(result);
						break;
					}
				}
			}
		}

		return probableMatches;
	}

	public static SubtitleDescriptor getBestMatch(File file, Collection<SubtitleDescriptor> subtitles, boolean strict) {
		if (file == null || subtitles == null || subtitles.isEmpty()) {
			return null;
		}

		try {
			// add other possible matches to the options
			SimilarityMetric sanity = SubtitleMetrics.verificationMetric();
			float minMatchSimilarity = strict ? 0.8f : 0.2f;

			// first match everything as best as possible, then filter possibly bad matches
			for (Entry<File, SubtitleDescriptor> it : matchSubtitles(singleton(file), subtitles).entrySet()) {
				if (sanity.getSimilarity(it.getKey(), it.getValue()) >= minMatchSimilarity) {
					return it.getValue();
				}
			}
			return null;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Detect charset and parse subtitle file even if extension is invalid
	 */
	public static List<SubtitleElement> decodeSubtitles(MemoryFile file) throws IOException {
		// gather all formats, put likely formats first
		LinkedList<SubtitleFormat> likelyFormats = new LinkedList<SubtitleFormat>();

		for (SubtitleFormat format : SubtitleFormat.values()) {
			if (format.getFilter().accept(file.getName()))
				likelyFormats.addFirst(format);
			else
				likelyFormats.addLast(format);
		}

		// decode bytes and beware of byte-order marks
		Reader reader = createTextReader(new ByteBufferInputStream(file.getData()), true, UTF_8);
		String content = IOUtils.toString(reader);

		// decode subtitle file with the first reader that seems to work
		for (SubtitleFormat format : likelyFormats) {
			List<SubtitleElement> subtitles = format.getDecoder().decode(content).collect(toList());

			if (subtitles.size() > 0) {
				return subtitles;
			}
		}

		// unsupported subtitle format
		throw new IOException("Subtitle format not supported");
	}

	public static ByteBuffer exportSubtitles(MemoryFile file, SubtitleFormat outputFormat, long outputTimingOffset, Charset outputEncoding) throws IOException {
		if (outputFormat != null && outputFormat != SubtitleFormat.SubRip) {
			throw new IllegalArgumentException("Format not supported");
		}

		ByteBufferOutputStream buffer = new ByteBufferOutputStream(file.size());
		OutputStreamWriter writer = new OutputStreamWriter(buffer, outputEncoding);

		if (outputFormat == SubtitleFormat.SubRip) {
			// convert to target format and target encoding
			try (SubRipWriter out = new SubRipWriter(writer)) {
				for (SubtitleElement it : decodeSubtitles(file)) {
					if (it.isEmpty()) {
						debug.warning(message("Subtitle element is empty", it));
						continue;
					}

					// adjust offset if necessary
					if (outputTimingOffset != 0) {
						it = new SubtitleElement(Math.max(0, it.getStart() + outputTimingOffset), Math.max(0, it.getEnd() + outputTimingOffset), it.getText());
					}

					out.write(it);
				}
			}
		} else {
			// convert only text encoding
			Reader reader = createTextReader(new ByteBufferInputStream(file.getData()), true, UTF_8);
			IOUtils.copy(reader, writer);
		}

		return buffer.getByteBuffer();
	}

	public static SubtitleFormat getSubtitleFormat(File file) {
		for (SubtitleFormat it : SubtitleFormat.values()) {
			if (it.getFilter().accept(file))
				return it;
		}

		return null;
	}

	public static SubtitleFormat getSubtitleFormatByName(String name) {
		for (SubtitleFormat it : SubtitleFormat.values()) {
			// check by name
			if (it.name().equalsIgnoreCase(name))
				return it;

			// check by extension
			if (it.getFilter().acceptExtension(name))
				return it;
		}

		return null;
	}

	public static String formatSubtitle(String name, String languageName, String type) {
		StringBuilder sb = new StringBuilder(name);

		if (languageName != null) {
			String lang = Language.getStandardLanguageCode(languageName);

			if (lang == null) {
				// we probably won't get here, but just in case
				lang = languageName.replaceAll("\\W", "");
			}

			sb.append('.').append(lang);
		}

		if (type != null) {
			sb.append('.').append(type);
		}

		return sb.toString();
	}

	public static MemoryFile fetchSubtitle(SubtitleDescriptor descriptor) throws Exception {
		ByteBuffer data = descriptor.fetch();

		// extract subtitles from archive
		ArchiveType type = ArchiveType.forName(descriptor.getType());

		if (type != ArchiveType.UNKOWN) {
			// extract subtitle from archive
			Iterator<MemoryFile> it = type.fromData(data).iterator();
			while (it.hasNext()) {
				MemoryFile entry = it.next();
				if (SUBTITLE_FILES.accept(entry.getName())) {
					return entry;
				}
			}
		}

		// assume that the fetched data is the subtitle
		return new MemoryFile(descriptor.getPath(), data);
	}

	public static Language detectSubtitleLanguage(File file) throws IOException {
		// grep language from filename
		Locale languageTag = releaseInfo.getSubtitleLanguageTag(getName(file));
		if (languageTag != null) {
			return Language.getLanguage(languageTag);
		}

		// check if file name matches a language name (e.g. English.srt)
		Language languageName = Language.findLanguage(getName(file));
		if (languageName != null) {
			return languageName;
		}

		// detect language from subtitle text content
		MemoryFile data = new MemoryFile(file.getName(), ByteBuffer.wrap(readFile(file)));
		List<DetectedLanguage> options = detectSubtitleLanguage(data);
		if (options.size() > 0) {
			return Language.getLanguage(options.get(0).getLocale().getLanguage());
		}

		return null;
	}

	public static List<DetectedLanguage> detectSubtitleLanguage(MemoryFile file) throws IOException {
		// decode subtitles
		String text = decodeSubtitles(file).stream().map(SubtitleElement::getText).collect(Collectors.joining("\n"));

		// detect text language
		return createLanguageDetector().getProbabilities(text);
	}

	private static LanguageDetectorBuilder languageDetector;

	private static LanguageDetector createLanguageDetector() throws IOException {
		if (languageDetector == null) {
			// load all language profiles and build language detector
			List<LdLocale> languages = BuiltInLanguages.getLanguages().stream().filter(lc -> Language.getLanguage(lc.getLanguage()) != null).collect(Collectors.toList());
			List<LanguageProfile> languageProfiles = new LanguageProfileReader().readBuiltIn(languages);
			languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard()).withProfiles(languageProfiles);
		}
		return languageDetector.build();
	}

	private SubtitleUtilities() {
		throw new UnsupportedOperationException();
	}

}
