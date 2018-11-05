package net.filebot.cli;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.Settings.*;
import static net.filebot.WebServices.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.filebot.HistorySpooler;
import net.filebot.Language;
import net.filebot.RenameAction;
import net.filebot.StandardRenameAction;
import net.filebot.archive.Archive;
import net.filebot.archive.FileMapper;
import net.filebot.format.ExpressionFileFormat;
import net.filebot.format.ExpressionFilter;
import net.filebot.format.ExpressionFormat;
import net.filebot.format.MediaBindingBean;
import net.filebot.hash.HashType;
import net.filebot.hash.VerificationFileReader;
import net.filebot.hash.VerificationFileWriter;
import net.filebot.media.AutoDetection;
import net.filebot.media.AutoDetection.Group;
import net.filebot.media.AutoDetection.Type;
import net.filebot.media.LocalDatasource;
import net.filebot.media.VideoQuality;
import net.filebot.similarity.CommonSequenceMatcher;
import net.filebot.similarity.EpisodeMatcher;
import net.filebot.similarity.Match;
import net.filebot.subtitle.SubtitleFormat;
import net.filebot.subtitle.SubtitleNaming;
import net.filebot.util.EntryList;
import net.filebot.util.FileUtilities.ParentFilter;
import net.filebot.vfs.FileInfo;
import net.filebot.vfs.MemoryFile;
import net.filebot.vfs.SimpleFileInfo;
import net.filebot.web.AudioTrack;
import net.filebot.web.Datasource;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.Movie;
import net.filebot.web.MovieIdentificationService;
import net.filebot.web.MoviePart;
import net.filebot.web.MusicIdentificationService;
import net.filebot.web.OpenSubtitlesClient;
import net.filebot.web.SearchResult;
import net.filebot.web.SortOrder;
import net.filebot.web.SubtitleDescriptor;
import net.filebot.web.SubtitleProvider;
import net.filebot.web.VideoHashSubtitleService;

public class CmdlineOperations implements CmdlineInterface {

	@Override
	public List<File> rename(Collection<File> files, RenameAction action, ConflictAction conflict, File output, ExpressionFileFormat format, Datasource db, String query, SortOrder order, ExpressionFilter filter, Locale locale, boolean strict, ExecCommand exec) throws Exception {
		// movie mode
		if (db instanceof MovieIdentificationService) {
			return renameMovie(files, action, conflict, output, format, (MovieIdentificationService) db, query, filter, locale, strict, exec);
		}

		// series mode
		if (db instanceof EpisodeListProvider) {
			return renameSeries(files, action, conflict, output, format, (EpisodeListProvider) db, query, order, filter, locale, strict, exec);
		}

		// music mode
		if (db instanceof MusicIdentificationService) {
			return renameMusic(files, action, conflict, output, format, singletonList((MusicIdentificationService) db), exec);
		}

		// photo / xattr / plain file mode
		if (db instanceof LocalDatasource) {
			return renameFiles(files, action, conflict, output, format, (LocalDatasource) db, filter, strict, exec);
		}

		// auto-detect mode for each fileset
		AutoDetection auto = new AutoDetection(files, false, locale);
		List<File> results = new ArrayList<File>();

		for (Entry<Group, Set<File>> it : auto.group().entrySet()) {
			if (it.getKey().types().length == 1) {
				for (Type key : it.getKey().types()) {
					switch (key) {
					case Movie:
						results.addAll(renameMovie(it.getValue(), action, conflict, output, format, TheMovieDB, query, filter, locale, strict, exec));
						break;
					case Series:
						results.addAll(renameSeries(it.getValue(), action, conflict, output, format, TheTVDB, query, order, filter, locale, strict, exec));
						break;
					case Anime:
						results.addAll(renameSeries(it.getValue(), action, conflict, output, format, AniDB, query, order, filter, locale, strict, exec));
						break;
					case Music:
						results.addAll(renameMusic(it.getValue(), action, conflict, output, format, asList(MediaInfoID3, AcoustID), exec)); // prefer existing ID3 tags and use acoustid only when necessary
						break;
					}
				}
			} else {
				debug.warning(format("Failed to process group: %s => %s", it.getKey(), it.getValue()));
			}
		}

		if (results.isEmpty()) {
			throw new CmdlineException("Failed to identify or process any files");
		}

		return results;
	}

	@Override
	public List<File> rename(EpisodeListProvider db, String query, ExpressionFileFormat format, ExpressionFilter filter, SortOrder order, Locale locale, boolean strict, List<File> files, RenameAction action, ConflictAction conflict, File outputDir, ExecCommand exec) throws Exception {
		// match files and episodes in linear order
		List<Episode> episodes = fetchEpisodeList(db, query, filter, order, locale, strict);

		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		for (int i = 0; i < files.size() && i < episodes.size(); i++) {
			matches.add(new Match<File, Episode>(files.get(i), episodes.get(i)));
		}

		// rename episodes
		return renameAll(formatMatches(matches, format, outputDir), action, conflict, matches, exec);
	}

	@Override
	public List<File> rename(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflict) throws Exception {
		// generic rename function that can be passed any set of files
		return renameAll(renameMap, renameAction, conflict, null, null);
	}

	public List<File> renameSeries(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, EpisodeListProvider db, String query, SortOrder sortOrder, ExpressionFilter filter, Locale locale, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename episodes using [%s]", db.getName()));

		// ignore sample files
		List<File> fileset = sortByUniquePath(filter(files, not(getClutterFileFilter())));

		List<File> mediaFiles = filter(fileset, VIDEO_FILES, SUBTITLE_FILES);
		if (mediaFiles.isEmpty()) {
			throw new CmdlineException("No media files: " + files);
		}

		// similarity metrics for matching
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// auto-determine optimal batch sets
		for (Entry<Set<File>, Set<String>> sameSeriesGroup : mapSeriesNamesByFiles(mediaFiles, locale, db == AniDB).entrySet()) {
			List<List<File>> batchSets = new ArrayList<List<File>>();

			if (sameSeriesGroup.getValue() != null && sameSeriesGroup.getValue().size() > 0) {
				// handle series name batch set all at once
				batchSets.add(new ArrayList<File>(sameSeriesGroup.getKey()));
			} else {
				// these files don't seem to belong to any series -> handle folder per folder
				batchSets.addAll(mapByFolder(sameSeriesGroup.getKey()).values());
			}

			for (List<File> batch : batchSets) {
				// fetch episode data
				List<Episode> episodes;

				if (query == null) {
					Collection<String> seriesNames = detectSeriesNames(batch, db == AniDB, locale); // detect series name by common word sequence
					log.config("Auto-detected query: " + seriesNames);

					if (seriesNames.size() == 0) {
						log.warning("Failed to detect query for files: " + batch);
						continue;
					}

					if (strict && seriesNames.size() > 1) {
						throw new CmdlineException("Multiple queries: Processing multiple shows at once requires -non-strict matching: " + seriesNames);
					}

					episodes = fetchEpisodeSet(db, seriesNames, sortOrder, locale, strict, 5); // consider episodes of up to N search results for each query
				} else {
					if (isSeriesID(query)) {
						episodes = db.getEpisodeList(Integer.parseInt(query), sortOrder, locale);
					} else {
						episodes = fetchEpisodeSet(db, singleton(query), sortOrder, locale, false, 1); // use --q option and pick first result
					}
				}

				if (episodes.isEmpty()) {
					continue;
				}

				// filter episodes
				episodes = applyExpressionFilter(episodes, filter);

				for (List<File> filesPerType : mapByMediaExtension(filter(batch, VIDEO_FILES, SUBTITLE_FILES)).values()) {
					matches.addAll(matchEpisodes(filesPerType, episodes, strict));
				}
			}
		}

		if (matches.isEmpty()) {
			throw new CmdlineException("Failed to match files to episode data");
		}

		// handle derived files
		List<Match<File, ?>> derivateMatches = new ArrayList<Match<File, ?>>();
		SortedSet<File> derivateFiles = new TreeSet<File>(fileset);
		derivateFiles.removeAll(mediaFiles);

		for (File file : derivateFiles) {
			for (Match<File, ?> match : matches) {
				if (file.getPath().startsWith(match.getValue().getParentFile().getPath()) && isDerived(file, match.getValue()) && match.getCandidate() instanceof Episode) {
					derivateMatches.add(new Match<File, Object>(file, ((Episode) match.getCandidate()).clone()));
					break;
				}
			}
		}

		// add matches from other files that are linked via filenames
		matches.addAll(derivateMatches);

		// rename episodes
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, matches, exec);
	}

	private List<Match<File, Object>> matchEpisodes(Collection<File> files, Collection<Episode> episodes, boolean strict) throws Exception {
		// always use strict fail-fast matcher
		EpisodeMatcher matcher = new EpisodeMatcher(files, episodes, strict);
		List<Match<File, Object>> matches = matcher.match();

		for (File failedMatch : matcher.remainingValues()) {
			log.warning("No matching episode: " + failedMatch.getName());
		}

		// in non-strict mode just pass back results as we got it from the matcher
		if (!strict) {
			return matches;
		}

		// in strict mode sanity check the result and only pass back good matches
		List<Match<File, Object>> validMatches = new ArrayList<Match<File, Object>>();
		for (Match<File, Object> it : matches) {
			if (isEpisodeNumberMatch(it.getValue(), (Episode) it.getCandidate())) {
				validMatches.add(it);
			}
		}
		return validMatches;
	}

	private List<Episode> fetchEpisodeSet(EpisodeListProvider db, Collection<String> names, SortOrder sortOrder, Locale locale, boolean strict, int limit) throws Exception {
		Set<SearchResult> shows = new LinkedHashSet<SearchResult>();
		Set<Episode> episodes = new LinkedHashSet<Episode>();

		// detect series names and create episode list fetch tasks
		for (String query : names) {
			List<SearchResult> results = db.search(query, locale);

			// select search result
			if (results.size() > 0) {
				List<SearchResult> selectedSearchResults = selectSearchResult(query, results, true, true, strict, limit);

				if (selectedSearchResults != null) {
					for (SearchResult it : selectedSearchResults) {
						if (shows.add(it)) {
							try {
								log.fine(format("Fetching episode data for [%s]", it.getName()));
								episodes.addAll(db.getEpisodeList(it, sortOrder, locale));
							} catch (IOException e) {
								throw new CmdlineException(String.format("Failed to fetch episode data for [%s]: %s", it, e.getMessage()), e);
							}
						}
					}
				}
			}
		}

		if (episodes.isEmpty()) {
			log.warning("Failed to fetch episode data: " + names);
		}

		return new ArrayList<Episode>(episodes);
	}

	public List<File> renameMovie(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, MovieIdentificationService service, String query, ExpressionFilter filter, Locale locale, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename movies using [%s]", service.getName()));

		// ignore sample files
		List<File> fileset = sortByUniquePath(filter(files, not(getClutterFileFilter())));

		// handle movie files
		Set<File> movieFiles = new TreeSet<File>(filter(fileset, VIDEO_FILES));
		Set<File> nfoFiles = new TreeSet<File>(filter(fileset, NFO_FILES));

		List<File> orphanedFiles = new ArrayList<File>(filter(fileset, FILES));
		orphanedFiles.removeAll(movieFiles);
		orphanedFiles.removeAll(nfoFiles);

		Map<File, List<File>> derivatesByMovieFile = new HashMap<File, List<File>>();
		for (File movieFile : movieFiles) {
			derivatesByMovieFile.put(movieFile, new ArrayList<File>());
		}
		for (File file : orphanedFiles) {
			List<File> orphanParent = listPath(file);
			for (File movieFile : movieFiles) {
				if (orphanParent.contains(movieFile.getParentFile()) && isDerived(file, movieFile)) {
					derivatesByMovieFile.get(movieFile).add(file);
					break;
				}
			}
		}
		for (List<File> derivates : derivatesByMovieFile.values()) {
			orphanedFiles.removeAll(derivates);
		}

		// match movie hashes online
		Map<File, Movie> movieByFile = new TreeMap<File, Movie>();
		if (query == null) {
			// collect useful nfo files even if they are not part of the selected fileset
			Set<File> effectiveNfoFileSet = new TreeSet<File>(nfoFiles);
			for (File dir : mapByFolder(movieFiles).keySet()) {
				effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
			}
			for (File dir : filter(fileset, FOLDERS)) {
				effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
			}

			for (File nfo : effectiveNfoFileSet) {
				try {
					Movie movie = grepMovie(nfo, service, locale);

					// ignore illegal nfos
					if (movie == null) {
						continue;
					}

					if (nfoFiles.contains(nfo)) {
						movieByFile.put(nfo, movie);
					}

					if (isDiskFolder(nfo.getParentFile())) {
						// special handling for disk folders
						for (File folder : fileset) {
							if (nfo.getParentFile().equals(folder)) {
								movieByFile.put(folder, movie);
							}
						}
					} else {
						// match movie info to movie files that match the nfo file name
						SortedSet<File> siblingMovieFiles = new TreeSet<File>(filter(movieFiles, new ParentFilter(nfo.getParentFile())));
						String baseName = stripReleaseInfo(getName(nfo)).toLowerCase();

						for (File movieFile : siblingMovieFiles) {
							if (!baseName.isEmpty() && stripReleaseInfo(getName(movieFile)).toLowerCase().startsWith(baseName)) {
								movieByFile.put(movieFile, movie);
							}
						}
					}
				} catch (Exception e) {
					log.log(Level.WARNING, "Failed to grep IMDbID: " + nfo.getName(), e);
				}
			}
		} else {
			log.fine(format("Looking up movie by query [%s]", query));
			List<Movie> results = service.searchMovie(query, locale);
			List<Movie> options = applyExpressionFilter(results, filter);
			if (options.isEmpty()) {
				throw new CmdlineException("Failed to find a valid match: " + results);
			}

			// force all mappings
			Movie movie = selectSearchResult(query, options);
			for (File file : files) {
				movieByFile.put(file, movie);
			}
		}

		// collect files that will be matched one by one
		List<File> movieMatchFiles = new ArrayList<File>();
		movieMatchFiles.addAll(movieFiles);
		movieMatchFiles.addAll(nfoFiles);
		movieMatchFiles.addAll(filter(files, FOLDERS));
		movieMatchFiles.addAll(filter(orphanedFiles, SUBTITLE_FILES)); // run movie detection only on orphaned subtitle files

		// sanity check that we have something to do
		if (fileset.isEmpty() || movieMatchFiles.isEmpty()) {
			throw new CmdlineException("No media files: " + files);
		}

		// map movies to (possibly multiple) files (in natural order)
		Map<Movie, SortedSet<File>> filesByMovie = new HashMap<Movie, SortedSet<File>>();

		// map all files by movie
		for (File file : movieMatchFiles) {
			Movie movie = movieByFile.get(file);

			// unknown hash, try via imdb id from nfo file
			if (movie == null) {
				log.fine(format("Auto-detect movie from context: [%s]", file));
				List<Movie> options = detectMovieWithYear(file, service, locale, strict);

				// ignore files that cannot yield any acceptable matches (e.g. movie files without year in strict mode)
				if (options == null) {
					continue;
				}

				// apply filter if defined
				options = applyExpressionFilter(options, filter);

				// reduce options to perfect matches if possible
				List<Movie> perfectMatches = matchMovieByWordSequence(getName(file), options, 0);

				// narrow down options if possible
				if (perfectMatches.size() > 0) {
					options = perfectMatches;
				}

				try {
					// select first element if matches are reliable
					if (options.size() > 0) {
						movie = selectSearchResult(stripReleaseInfo(getName(file)), options);

						// make sure to get the language-specific movie object for the selected option
						movie = getLocalizedMovie(service, movie, locale);
					}
				} catch (Exception e) {
					log.warning(cause(e));
				}
			}

			// check if we managed to lookup the movie descriptor
			if (movie != null) {
				// add to file list for movie
				filesByMovie.computeIfAbsent(movie, k -> new TreeSet<File>()).add(file);
			}
		}

		// collect all File/MoviePart matches
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		filesByMovie.forEach((movie, fs) -> {
			groupByMediaCharacteristics(fs).forEach(moviePartFiles -> {
				// resolve movie parts
				for (int i = 0; i < moviePartFiles.size(); i++) {
					Movie moviePart = moviePartFiles.size() == 1 ? movie : new MoviePart(movie, i + 1, moviePartFiles.size());
					matches.add(new Match<File, Movie>(moviePartFiles.get(i), moviePart.clone()));

					// automatically add matches for derived files
					List<File> derivates = derivatesByMovieFile.get(moviePartFiles.get(i));
					if (derivates != null) {
						for (File derivate : derivates) {
							matches.add(new Match<File, Movie>(derivate, moviePart.clone()));
						}
					}
				}
			});
		});

		// rename movies
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, matches, exec);
	}

	public List<File> renameMusic(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, List<MusicIdentificationService> services, ExecCommand exec) throws Exception {
		List<File> audioFiles = sortByUniquePath(filter(files, AUDIO_FILES, VIDEO_FILES));

		// check audio files against all services if necessary
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		LinkedHashSet<File> remaining = new LinkedHashSet<File>(audioFiles);

		// check audio files against all services
		for (MusicIdentificationService service : services) {
			if (remaining.size() > 0) {
				log.config(format("Rename music using %s", service.getIdentifier()));
				service.lookup(remaining).forEach((file, music) -> {
					if (music != null) {
						matches.add(new Match<File, AudioTrack>(file, music.clone()));
						remaining.remove(file);
					}
				});
			}
		}

		// error logging
		remaining.forEach(f -> log.warning(format("Failed to process music file: %s", f)));

		// rename movies
		return renameAll(formatMatches(matches, format, outputDir), renameAction, conflictAction, null, exec);
	}

	public List<File> renameFiles(Collection<File> files, RenameAction renameAction, ConflictAction conflictAction, File outputDir, ExpressionFileFormat format, LocalDatasource service, ExpressionFilter filter, boolean strict, ExecCommand exec) throws Exception {
		log.config(format("Rename files using [%s]", service.getName()));

		Map<File, File> renameMap = new LinkedHashMap<File, File>();

		// match to xattr metadata object or the file itself
		Map<File, Object> matches = service.match(files, strict);

		service.match(files, strict).forEach((k, v) -> {
			MediaBindingBean bindingBean = new MediaBindingBean(v, k, matches);

			if (filter == null || filter.matches(bindingBean)) {
				String destinationPath = format != null ? format.format(bindingBean) : v instanceof File ? v.toString() : validateFileName(v.toString());
				renameMap.put(k, getDestinationFile(k, destinationPath, outputDir));
			}
		});

		return renameAll(renameMap, renameAction, conflictAction, null, exec);
	}

	private Map<File, Object> getContext(List<Match<File, ?>> matches) {
		return new AbstractMap<File, Object>() {

			@Override
			public Set<Entry<File, Object>> entrySet() {
				return matches.stream().collect(toMap(it -> it.getValue(), it -> (Object) it.getCandidate(), (a, b) -> a, LinkedHashMap::new)).entrySet();
			}
		};
	}

	private File getDestinationFile(File original, String newName, File outputDir) {
		String extension = getExtension(original);
		File newFile = new File(extension != null ? newName + '.' + extension.toLowerCase() : newName);

		// resolve against output dir
		if (outputDir != null && !newFile.isAbsolute()) {
			newFile = new File(outputDir, newFile.getPath());
		}

		if (isInvalidFilePath(newFile) && !isUnixFS()) {
			log.config("Stripping invalid characters from new path: " + newName);
			newFile = validateFilePath(newFile);
		}

		return newFile;
	}

	private Map<File, File> formatMatches(List<Match<File, ?>> matches, ExpressionFileFormat format, File outputDir) throws Exception {
		// map old files to new paths by applying formatting and validating filenames
		Map<File, File> renameMap = new LinkedHashMap<File, File>();

		for (Match<File, ?> match : matches) {
			File file = match.getValue();
			Object object = match.getCandidate();
			String destinationPath = format != null ? format.format(new MediaBindingBean(object, file, getContext(matches))) : validateFileName(object.toString());

			renameMap.put(file, getDestinationFile(file, destinationPath, outputDir));
		}

		return renameMap;
	}

	protected List<File> renameAll(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflictAction, List<Match<File, ?>> matches, ExecCommand exec) throws Exception {
		if (renameMap.isEmpty()) {
			throw new CmdlineException("Failed to identify or process any files");
		}

		// allow --action test for evaluation purposes
		if (renameAction != StandardRenameAction.TEST) {
			//LICENSE.check();
		}

		// rename files
		Map<File, File> renameLog = new LinkedHashMap<File, File>();

		try {
			for (Entry<File, File> it : renameMap.entrySet()) {
				try {
					File source = it.getKey();
					File destination = it.getValue();

					// resolve destination
					if (!destination.isAbsolute()) {
						// same folder, different name
						destination = resolve(source, destination);
					}

					if (!destination.equals(source) && existsNoFollowLinks(destination)) {
						if (conflictAction == ConflictAction.FAIL) {
							throw new CmdlineException(String.format("Failed to process [%s] because [%s] already exists", source, destination));
						}

						// do not allow abuse of online databases by repeatedly processing the same files
						if (matches != null && renameAction.canRevert() && source.length() > 0 && equalsFileContent(source, destination)) {
							throw new CmdlineException(String.format("Failed to process [%s] because [%s] is an exact copy and already exists", source, destination));
						}

						// delete existing destination path if necessary
						if (conflictAction == ConflictAction.OVERRIDE || (conflictAction == ConflictAction.AUTO && VideoQuality.isBetter(source, destination))) {
							// do not delete files in test mode
							if (renameAction.canRevert()) {
								try {
									log.fine(format("[%s] Delete [%s]", conflictAction, destination));
									delete(destination);
								} catch (Exception e) {
									log.warning(format("[%s] Failed to delete [%s]: %s", conflictAction, destination, e));
								}
							}
						}

						// generate indexed destination path if necessary
						if (conflictAction == ConflictAction.INDEX) {
							destination = nextAvailableIndexedName(destination);
						}
					}

					// rename file, throw exception on failure
					if (!destination.equals(source) && !destination.exists()) {
						log.info(format("[%s] from [%s] to [%s]", renameAction, source, destination));
						destination = renameAction.rename(source, destination);

						// remember successfully renamed matches for history entry and possible revert
						renameLog.put(source, destination);
					} else {
						log.info(format("Skipped [%s] because [%s] already exists", source, destination));
					}
				} catch (IOException e) {
					log.warning(format("[%s] Failure: %s", renameAction, e));
					throw e;
				}
			}
		} finally {
			// update history and xattr metadata
			if (renameLog.size() > 0) {
				writeHistory(renameAction, renameLog, matches);
			}

			// print number of processed files
			log.fine(format("Processed %d files", renameLog.size()));
		}

		// execute command
		if (exec != null) {
			try {
				execute(renameLog.values(), Objects::nonNull, exec); // destination files may include null values
			} catch (Exception e) {
				log.warning(message("Execute", e.getMessage()));
			}
		}

		return new ArrayList<File>(renameLog.values());
	}

	protected void writeHistory(RenameAction action, Map<File, File> log, List<Match<File, ?>> matches) {
		// write rename history
		if (action.canRevert()) {
			HistorySpooler.getInstance().append(log.entrySet());
		}

		// write xattr metadata
		if (matches != null) {
			for (Match<File, ?> match : matches) {
				if (match.getCandidate() != null) {
					File destination = log.get(match.getValue());
					if (destination != null && destination.isFile()) {
						xattr.setMetaInfo(destination, match.getCandidate(), match.getValue().getName());
					}
				}
			}
		}
	}

	protected File nextAvailableIndexedName(File file) {
		File parent = file.getParentFile();
		String name = getName(file);
		String ext = getExtension(file);
		return IntStream.range(1, 100).mapToObj(i -> new File(parent, name + '.' + i + '.' + ext)).filter(f -> !f.exists()).findFirst().get();
	}

	@Override
	public List<File> getSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception {
		// ignore anything that is not a video
		files = filter(files, VIDEO_FILES);

		// ignore sample files
		files = sortByUniquePath(filter(files, not(getClutterFileFilter())));

		// try to find subtitles for each video file
		List<File> remainingVideos = new ArrayList<File>(files);

		// parallel download
		List<File> subtitleFiles = new ArrayList<File>();

		log.finest(format("Get [%s] subtitles for %d files", language.getName(), remainingVideos.size()));
		if (remainingVideos.isEmpty()) {
			throw new CmdlineException("No video files: " + files);
		}

		// lookup subtitles by hash
		for (VideoHashSubtitleService service : getVideoHashSubtitleServices(language.getLocale())) {
			if (remainingVideos.isEmpty() || !requireLogin(service)) {
				continue;
			}

			try {
				log.fine("Looking up subtitles by hash via " + service.getName());
				Map<File, List<SubtitleDescriptor>> options = lookupSubtitlesByHash(service, remainingVideos, language.getLocale(), false, strict);
				Map<File, File> downloads = downloadSubtitleBatch(service, options, output, encoding, format);
				remainingVideos.removeAll(downloads.keySet());
				subtitleFiles.addAll(downloads.values());
			} catch (Exception e) {
				log.warning("Lookup by hash failed: " + e.getMessage());
			}
		}

		for (SubtitleProvider service : getSubtitleProviders(language.getLocale())) {
			if (strict || remainingVideos.isEmpty() || !requireLogin(service)) {
				continue;
			}

			try {
				log.fine(format("Looking up subtitles by name via %s", service.getName()));
				Map<File, List<SubtitleDescriptor>> options = findSubtitlesByName(service, remainingVideos, language.getLocale(), query, false, strict);
				Map<File, File> downloads = downloadSubtitleBatch(service, options, output, encoding, format);
				remainingVideos.removeAll(downloads.keySet());
				subtitleFiles.addAll(downloads.values());
			} catch (Exception e) {
				log.warning(format("Search by name failed: %s", e.getMessage()));
			}
		}

		// no subtitles for remaining video files
		for (File it : remainingVideos) {
			log.warning("No matching subtitles found: " + it);
		}

		return subtitleFiles;
	}

	protected static boolean requireLogin(Object service) {
		if (service instanceof OpenSubtitlesClient) {
			OpenSubtitlesClient osdb = (OpenSubtitlesClient) service;
			if (osdb.isAnonymous()) {
				throw new CmdlineException(String.format("%s: Please enter your login details by calling `filebot -script fn:configure`", osdb.getName()));
			}
		}
		return true; // no login => logged in by default
	}

	@Override
	public List<File> getMissingSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception {
		List<File> videoFiles = filter(filter(files, VIDEO_FILES), new FileFilter() {

			// save time on repeating filesystem calls
			private Map<File, List<File>> cache = new HashMap<File, List<File>>();

			public boolean matchesLanguageCode(File f) {
				Language languageSuffix = Language.getLanguage(releaseInfo.getSubtitleLanguageTag(getName(f)));
				if (languageSuffix != null) {
					return languageSuffix.getCode().equals(language.getCode());
				}
				return false;
			}

			@Override
			public boolean accept(File video) {
				if (!video.isFile()) {
					return false;
				}

				List<File> subtitleFiles = cache.computeIfAbsent(video.getParentFile(), parent -> {
					return getChildren(parent, SUBTITLE_FILES);
				});

				// can't tell which subtitle belongs to which file -> if any subtitles exist skip the whole folder
				if (format == SubtitleNaming.ORIGINAL) {
					return subtitleFiles.size() == 0;
				}

				return subtitleFiles.stream().allMatch(f -> {
					if (isDerived(f, video)) {
						return format != SubtitleNaming.MATCH_VIDEO && !matchesLanguageCode(f);
					}
					return true;
				});
			}
		});

		if (videoFiles.isEmpty()) {
			log.info("No missing subtitles");
			return emptyList();
		}

		return getSubtitles(videoFiles, query, language, output, encoding, format, strict);
	}

	private Map<File, File> downloadSubtitleBatch(Datasource service, Map<File, List<SubtitleDescriptor>> subtitles, SubtitleFormat outputFormat, Charset outputEncoding, SubtitleNaming naming) {
		Map<File, File> downloads = new LinkedHashMap<File, File>();

		// fetch subtitle
		subtitles.forEach((movie, options) -> {
			if (options.size() > 0) {
				SubtitleDescriptor subtitle = options.get(0);
				try {
					downloads.put(movie, downloadSubtitle(service, subtitle, movie, outputFormat, outputEncoding, naming));
				} catch (Exception e) {
					log.warning(format("Failed to download %s: %s", subtitle, e));
				}
			}
		});

		return downloads;
	}

	private File downloadSubtitle(Datasource service, SubtitleDescriptor descriptor, File movieFile, SubtitleFormat outputFormat, Charset outputEncoding, SubtitleNaming naming) throws Exception {
		// fetch subtitle archive
		log.config(format("Fetching [%s] subtitles [%s] from [%s]", descriptor.getLanguageName(), descriptor.getPath(), service.getName()));
		MemoryFile subtitleFile = fetchSubtitle(descriptor);

		// subtitle filename is based on movie filename
		String extension = getExtension(subtitleFile.getName());
		ByteBuffer data = subtitleFile.getData();

		if (outputFormat != null || outputEncoding != null) {
			// adjust extension of the output file
			if (outputFormat != null) {
				extension = outputFormat.getFilter().extension();
			}

			// default to UTF-8 if no other encoding is given
			if (outputEncoding == null) {
				outputEncoding = UTF_8;
			}

			log.finest(format("Export [%s] as [%s / %s]", subtitleFile.getName(), outputFormat, outputEncoding));
			data = exportSubtitles(subtitleFile, outputFormat, 0, outputEncoding);
		}

		File destination = new File(movieFile.getParentFile(), naming.format(movieFile, descriptor, extension));
		log.info(format("Writing [%s] to [%s]", subtitleFile.getName(), destination.getName()));

		writeFile(data, destination);
		return destination;
	}

	protected <T> List<T> applyExpressionFilter(List<T> input, ExpressionFilter filter) {
		if (filter == null) {
			return input;
		}

		log.fine(format("Apply filter [%s] on [%d] items", filter.getExpression(), input.size()));

		return input.stream().filter(it -> {
			if (filter.matches(new MediaBindingBean(it, null, new EntryList<File, T>(null, input)))) {
				log.finest(format("Include [%s]", it));
				return true;
			}
			return false;
		}).collect(toList());
	}

	protected <T extends SearchResult> T selectSearchResult(String query, Collection<T> options) throws Exception {
		List<T> matches = selectSearchResult(query, options, false, false, false, 1);
		return matches.size() > 0 ? matches.get(0) : null;
	}

	protected <T extends SearchResult> List<T> selectSearchResult(String query, Collection<T> options, boolean sort, boolean alias, boolean strict, int limit) throws Exception {
		List<T> probableMatches = getProbableMatches(sort ? query : null, options, alias, strict);

		if (probableMatches.isEmpty() || (strict && probableMatches.size() != 1)) {
			// allow single search results to just pass through in non-strict mode even if match confidence is low
			if (options.size() == 1 && !strict) {
				return options.stream().collect(toList());
			}

			if (strict) {
				throw new CmdlineException("Multiple options: Advanced auto-selection requires -non-strict matching: " + probableMatches);
			}

			// just pick the best N matches
			if (sort) {
				probableMatches = sortBySimilarity(options, singleton(query), getSeriesMatchMetric()).stream().collect(toList());
			}
		}

		// return first and only value
		return probableMatches.size() <= limit ? probableMatches : probableMatches.subList(0, limit); // trust that the correct match is in the Top N
	}

	@Override
	public boolean check(Collection<File> files) throws Exception {
		// only check existing hashes
		boolean result = true;

		for (File it : filter(files, VERIFICATION_FILES)) {
			result &= check(it, it.getParentFile());
		}

		return result;
	}

	@Override
	public File compute(Collection<File> files, File output, HashType hash, Charset encoding) throws Exception {
		// ignore folders and any sort of special files
		files = filter(files, FILES);

		if (files.isEmpty()) {
			throw new CmdlineException("No files: " + files);
		}

		// find common parent folder of all files
		File[] fileList = files.toArray(new File[0]);
		File[][] pathArray = new File[fileList.length][];
		for (int i = 0; i < fileList.length; i++) {
			pathArray[i] = listPath(fileList[i].getParentFile()).toArray(new File[0]);
		}

		CommonSequenceMatcher csm = new CommonSequenceMatcher(null, 0, true);
		File[] common = csm.matchFirstCommonSequence(pathArray);

		if (common == null) {
			throw new CmdlineException("All paths must be on the same filesystem: " + files);
		}

		// last element in the common sequence must be the root folder
		File root = common[common.length - 1];

		if (output == null) {
			output = new File(root, root.getName() + '.' + hash.getFilter().extension());
		} else if (!output.isAbsolute()) {
			output = new File(root, output.getPath());
		}

		log.info(format("Compute %s hash for %s files [%s]", hash, files.size(), output));
		compute(root, files, output, hash, encoding);

		return output;
	}

	private boolean check(File verificationFile, File root) throws Exception {
		HashType type = getHashType(verificationFile);

		// check if type is supported
		if (type == null) {
			throw new CmdlineException("Unsupported format: " + verificationFile);
		}

		// add all file names from verification file
		log.fine(format("Checking [%s]", verificationFile.getName()));
		VerificationFileReader parser = new VerificationFileReader(createTextReader(verificationFile), type.getFormat());
		boolean status = true;

		try {
			while (parser.hasNext()) {
				try {
					Entry<File, String> it = parser.next();

					File file = new File(root, it.getKey().getPath()).getAbsoluteFile();
					String current = computeHash(new File(root, it.getKey().getPath()), type);
					log.info(format("%s %s", current, file));

					if (current.compareToIgnoreCase(it.getValue()) != 0) {
						throw new IOException(String.format("Corrupted file found: %s [hash mismatch: %s vs %s]", it.getKey(), current, it.getValue()));
					}
				} catch (IOException e) {
					status = false;
					log.warning(e.getMessage());
				}
			}
		} finally {
			parser.close();
		}

		return status;
	}

	private void compute(File root, Collection<File> files, File outputFile, HashType hashType, Charset encoding) throws IOException, Exception {
		// compute hashes recursively and write to file
		VerificationFileWriter out = new VerificationFileWriter(outputFile, hashType.getFormat(), encoding != null ? encoding : UTF_8);

		try {
			for (File it : files) {
				if (it.isHidden() || VERIFICATION_FILES.accept(it)) {
					continue;
				}

				String relativePath = normalizePathSeparators(it.getPath().substring(root.getPath().length() + 1)); // skip root and first slash
				String hash = computeHash(it, hashType);
				log.info(format("%s %s", hash, relativePath));

				out.write(relativePath, hash);
			}
		} catch (Exception e) {
			outputFile.deleteOnExit(); // delete only partially written files
			throw e;
		} finally {
			out.close();
		}
	}

	private List<Episode> fetchEpisodeList(EpisodeListProvider db, String query, ExpressionFilter filter, SortOrder order, Locale locale, boolean strict) throws Exception {
		// sanity check
		if (query == null) {
			throw new CmdlineException(String.format("%s: query parameter is required", db.getName()));
		}

		// collect all episode objects first
		List<Episode> episodes = new ArrayList<Episode>();

		if (isSeriesID(query)) {
			// lookup by id
			episodes.addAll(db.getEpisodeList(Integer.parseInt(query), order, locale));
		} else {
			// search by name and select search result
			List<SearchResult> options = selectSearchResult(query, db.search(query, locale), false, false, false, strict ? 1 : 5);

			// fetch episodes
			for (SearchResult option : options) {
				episodes.addAll(db.getEpisodeList(option, order, locale));
			}
		}

		// sanity check
		if (episodes.isEmpty()) {
			throw new CmdlineException(String.format("%s: no results", db.getName()));
		}

		// apply filter
		return applyExpressionFilter(episodes, filter);
	}

	private boolean isSeriesID(String query) {
		return query.matches("\\d{5,9}");
	}

	@Override
	public Stream<String> fetchEpisodeList(EpisodeListProvider db, String query, ExpressionFormat format, ExpressionFilter filter, SortOrder order, Locale locale, boolean strict) throws Exception {
		// collect all episode objects first
		List<Episode> episodes = fetchEpisodeList(db, query, filter, order, locale, strict);

		// instant format
		if (format == null) {
			return episodes.stream().map(Episode::toString);
		}

		// lazy format
		return episodes.stream().map(episode -> {
			try {
				return format.format(new MediaBindingBean(episode, null, new EntryList<File, Episode>(null, episodes)));
			} catch (Exception e) {
				debug.warning(e::getMessage);
			}
			return null;
		}).filter(Objects::nonNull);
	}

	@Override
	public Stream<String> getMediaInfo(Collection<File> files, FileFilter filter, ExpressionFormat format) throws Exception {
		// use default expression format if not set
		if (format == null) {
			return getMediaInfo(files, filter, new ExpressionFormat("{fn} [{resolution} {vc} {channels} {ac} {hours}]"));
		}

		return files.stream().filter(filter::accept).map(f -> {
			try {
				return format.format(new MediaBindingBean(xattr.getMetaInfo(f), f));
			} catch (Exception e) {
				debug.warning(e::getMessage);
			}
			return null;
		}).filter(Objects::nonNull);
	}

	@Override
	public boolean execute(Collection<File> files, FileFilter filter, ExecCommand exec) throws Exception {
		// collect files
		List<File> f = filter(files, filter);

		if (f.isEmpty()) {
			return false;
		}

		// collect object metadata
		List<Object> m = f.stream().map(xattr::getMetaInfo).collect(toList());

		// build and execute commands
		MediaBindingBean[] group = IntStream.range(0, f.size()).mapToObj(i -> new MediaBindingBean(m.get(i), f.get(i), new EntryList<File, Object>(f, m))).toArray(MediaBindingBean[]::new);
		exec.execute(group);

		return true;
	}

	@Override
	public List<File> revert(Collection<File> files, FileFilter filter, RenameAction action) throws Exception {
		if (files.isEmpty()) {
			throw new CmdlineException("Expecting at least one input path");
		}

		Set<File> whitelist = new HashSet<File>(files);
		Map<File, File> history = HistorySpooler.getInstance().getCompleteHistory().getRenameMap();

		return history.entrySet().stream().filter(it -> {
			File original = it.getKey();
			File current = it.getValue();
			return Stream.of(current, original).flatMap(f -> listPath(f).stream()).anyMatch(whitelist::contains) && current.exists() && filter.accept(current);
		}).map(it -> {
			File original = it.getKey();
			File current = it.getValue();

			log.info(format("Revert [%s] to [%s]", current, original));
			if (action.canRevert()) {
				try {
					return StandardRenameAction.revert(current, original);
				} catch (Exception e) {
					log.warning("Failed to revert file: " + e);
				}
			}
			return null;
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	public List<File> extract(Collection<File> files, File output, ConflictAction conflict, FileFilter filter, boolean forceExtractAll) throws Exception {
		// only keep single-volume archives or first part of multi-volume archives
		List<File> archiveFiles = filter(files, Archive.VOLUME_ONE_FILTER);
		List<File> extractedFiles = new ArrayList<File>();

		for (File file : archiveFiles) {
			Archive archive = Archive.open(file);
			try {
				File outputFolder = output;

				if (outputFolder == null || !outputFolder.isAbsolute()) {
					outputFolder = new File(file.getParentFile(), outputFolder == null ? getName(file) : outputFolder.getPath()).getCanonicalFile();
				}

				log.info(format("Read archive [%s] and extract to [%s]", file.getName(), outputFolder));
				FileMapper outputMapper = new FileMapper(outputFolder);

				List<FileInfo> outputMapping = new ArrayList<FileInfo>();
				for (FileInfo it : archive.listFiles()) {
					File outputPath = outputMapper.getOutputFile(it.toFile());
					outputMapping.add(new SimpleFileInfo(outputPath.getPath(), it.getLength()));
				}

				// print warning message if archive appears empty
				if (outputMapping.isEmpty()) {
					log.warning(format("[%s] contains [%s] files", file.getName(), outputMapping.size()));
				}

				Set<FileInfo> selection = new TreeSet<FileInfo>();
				for (FileInfo future : outputMapping) {
					if (filter == null || filter.accept(future.toFile())) {
						selection.add(future);
					}
				}

				// check if there is anything to extract at all
				if (selection.isEmpty()) {
					continue;
				}

				boolean skip = true;
				for (FileInfo future : filter == null || forceExtractAll ? outputMapping : selection) {
					if (conflict == ConflictAction.AUTO) {
						skip &= (future.toFile().exists() && future.getLength() == future.toFile().length());
					} else {
						skip &= (future.toFile().exists());
					}
				}

				if (!skip || conflict == ConflictAction.OVERRIDE) {
					if (filter == null || forceExtractAll) {
						log.finest("Extracting files " + outputMapping);

						// extract all files
						archive.extract(outputMapper.getOutputDir());

						for (FileInfo it : outputMapping) {
							extractedFiles.add(it.toFile());
						}
					} else {
						log.finest("Extracting files " + selection);

						// extract files selected by the given filter
						archive.extract(outputMapper.getOutputDir(), outputMapper.newPathFilter(selection));

						for (FileInfo it : selection) {
							extractedFiles.add(it.toFile());
						}
					}
				} else {
					log.finest("Skipped extracting files " + selection);
				}
			} finally {
				archive.close();
			}
		}

		return extractedFiles;
	}

}
