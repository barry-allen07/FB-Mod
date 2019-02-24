package net.filebot.ui.rename;

import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static javax.swing.BorderFactory.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.Settings.*;
import static net.filebot.WebServices.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.similarity.CommonSequenceMatcher.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.swing.JLabel;

import net.filebot.Cache;
import net.filebot.Cache.TypedCache;
import net.filebot.CacheType;
import net.filebot.similarity.EpisodeMatcher;
import net.filebot.similarity.Match;
import net.filebot.ui.SelectDialog;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.SearchResult;
import net.filebot.web.SortOrder;

class EpisodeListMatcher implements AutoCompleteMatcher {

	private EpisodeListProvider provider;
	private boolean anime;

	// remember user decisions
	private Map<String, SearchResult> selectionMemory = new TreeMap<String, SearchResult>(getLenientCollator(Locale.ENGLISH));
	private Map<String, List<String>> inputMemory = new TreeMap<String, List<String>>(getLenientCollator(Locale.ENGLISH));

	public EpisodeListMatcher(EpisodeListProvider provider, boolean anime) {
		this.provider = provider;
		this.anime = anime;
	}

	public TypedCache<SearchResult> getPersistentSelectionMemory() {
		return Cache.getCache("selection_" + provider.getName(), CacheType.Persistent).cast(SearchResult.class);
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder sortOrder, Locale locale, boolean autodetection, Component parent) throws Exception {
		if (files.isEmpty()) {
			return justFetchEpisodeList(sortOrder, locale, parent);
		}

		// ignore sample files
		List<File> fileset = autodetection ? filter(files, not(getClutterFileFilter())) : new ArrayList<File>(files);

		// focus on movie and subtitle files
		List<File> mediaFiles = filter(fileset, VIDEO_FILES, SUBTITLE_FILES);

		// merge episode matches
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		ExecutorService workerThreadPool = Executors.newFixedThreadPool(getPreferredThreadPoolSize());
		try {
			// detect series names and create episode list fetch tasks
			List<Future<List<Match<File, ?>>>> tasks = new ArrayList<Future<List<Match<File, ?>>>>();

			if (strict) {
				// in strict mode simply process file-by-file (ignoring all files that don't contain clear SxE patterns)
				mediaFiles.stream().filter(f -> isEpisode(f, false)).map(f -> {
					return workerThreadPool.submit(() -> {
						return matchEpisodeSet(singletonList(f), detectSeriesNames(singleton(f), anime, locale), sortOrder, strict, locale, autodetection, parent);
					});
				}).forEach(tasks::add);
			} else {
				// in non-strict mode use the complicated (more powerful but also more error prone) match-batch-by-batch logic
				mapSeriesNamesByFiles(mediaFiles, locale, anime).forEach((f, n) -> {
					// 1. handle series name batch set all at once -> only 1 batch set
					// 2. files don't seem to belong to any series -> handle folder per folder -> multiple batch sets
					Collection<List<File>> batches = n != null && n.size() > 0 ? singleton(new ArrayList<File>(f)) : mapByFolder(f).values();

					batches.stream().map(b -> {
						return workerThreadPool.submit(() -> {
							return matchEpisodeSet(b, n, sortOrder, strict, locale, autodetection, parent);
						});
					}).forEach(tasks::add);
				});
			}

			for (Future<List<Match<File, ?>>> future : tasks) {
				// make sure each episode has unique object data
				for (Match<File, ?> it : future.get()) {
					matches.add(new Match<File, Episode>(it.getValue(), ((Episode) it.getCandidate()).clone()));
				}
			}
		} finally {
			workerThreadPool.shutdownNow();
		}

		// handle derived files
		List<Match<File, ?>> derivateMatches = new ArrayList<Match<File, ?>>();
		Set<File> derivateFiles = new TreeSet<File>(fileset);
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

		// restore original order
		matches.sort(comparing(Match::getValue, OriginalOrder.of(files)));

		return matches;
	}

	public List<Match<File, ?>> matchEpisodeSet(List<File> files, Collection<String> queries, SortOrder sortOrder, boolean strict, Locale locale, boolean autodetection, Component parent) throws Exception {
		Collection<Episode> episodes = emptySet();

		// detect series name and fetch episode list
		if (autodetection) {
			if (queries != null && queries.size() > 0) {
				// only allow one fetch session at a time so later requests can make use of cached results
				episodes = fetchEpisodeSet(files, queries, sortOrder, locale, autodetection, parent);
			}
		}

		// require user input if auto-detection has failed or has been disabled
		if (episodes.isEmpty() && !strict) {
			List<String> detectedSeriesNames = detectSeriesNames(files, anime, locale);
			String suggestion = detectedSeriesNames.size() > 0 ? join(detectedSeriesNames, "; ") : normalizePunctuation(getName(files.get(0)));

			synchronized (inputMemory) {
				List<String> input = inputMemory.get(suggestion);
				if (input == null || suggestion == null || suggestion.isEmpty()) {
					input = showMultiValueInputDialog(getQueryInputMessage("Please identify the following files:", "Enter series name:", files), suggestion, provider.getName(), parent);
					inputMemory.put(suggestion, input);
				}

				if (input != null && input.size() > 0) {
					// only allow one fetch session at a time so later requests can make use of cached results
					episodes = fetchEpisodeSet(files, input, sortOrder, locale, false, parent);
				}
			}
		}

		// find file/episode matches
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		// group by subtitles first and then by files in general
		if (episodes.size() > 0) {
			for (List<File> filesPerType : mapByMediaExtension(files).values()) {
				EpisodeMatcher matcher = new EpisodeMatcher(filesPerType, episodes, strict);
				for (Match<File, Object> it : matcher.match()) {
					// in strict mode sanity check the result and only pass back good matches
					if (!strict || isEpisodeNumberMatch(it.getValue(), (Episode) it.getCandidate())) {
						matches.add(new Match<File, Episode>(it.getValue(), ((Episode) it.getCandidate()).clone()));
					}
				}
			}
		}

		return matches;
	}

	protected Set<Episode> fetchEpisodeSet(List<File> files, Collection<String> querySet, SortOrder sortOrder, Locale locale, boolean autodetection, Component parent) throws Exception {
		// only allow one fetch session at a time so later requests can make use of cached results
		// detect series names and fetch episode lists in parallel
		List<Future<List<Episode>>> tasks = querySet.stream().map(q -> {
			return requestThreadPool.submit(() -> {
				// select search result
				List<SearchResult> options = provider.search(q, locale);

				if (options.size() > 0) {
					SearchResult selectedSearchResult = selectSearchResult(files, q, options, autodetection, parent);
					if (selectedSearchResult != null) {
						return provider.getEpisodeList(selectedSearchResult, sortOrder, locale);
					}
				}
				return (List<Episode>) EMPTY_LIST;
			});
		}).collect(toList());

		// merge all episodes
		Set<Episode> episodes = new LinkedHashSet<Episode>();
		for (Future<List<Episode>> it : tasks) {
			episodes.addAll(it.get());
		}
		return episodes;
	}

	protected SearchResult selectSearchResult(List<File> files, String query, List<SearchResult> options, boolean autodetection, Component parent) throws Exception {
		if (options.size() == 1) {
			return options.get(0);
		}

		// auto-select most probable search result
		List<SearchResult> probableMatches = getProbableMatches(query, options, true, true);

		// auto-select first and only probable search result
		if (probableMatches.size() == 1) {
			return probableMatches.get(0);
		}

		// show selection dialog on EDT
		Callable<SearchResult> showSelectDialog = () -> {
			JLabel header = new JLabel(getQueryInputMessage("Failed to identify some of the following files:", null, getFilesForQuery(files, query)));
			header.setBorder(createCompoundBorder(createTitledBorder(""), createEmptyBorder(3, 3, 3, 3)));

			// multiple results have been found, user must select one
			SelectDialog<SearchResult> selectDialog = new SelectDialog<SearchResult>(parent, options, true, false, header.getText().isEmpty() ? null : header);
			selectDialog.setTitle(provider.getName());
			selectDialog.getMessageLabel().setText("<html>Select best match for \"<b>" + escapeHTML(query) + "</b>\":</html>");
			selectDialog.getCancelAction().putValue(Action.NAME, "Skip");
			selectDialog.pack();

			// show dialog
			selectDialog.restoreState(Preferences.userNodeForPackage(EpisodeListMatcher.class));
			selectDialog.setLocation(getOffsetLocation(selectDialog.getOwner()));
			selectDialog.setVisible(true);

			// remember dialog state
			selectDialog.saveState(Preferences.userNodeForPackage(EpisodeListMatcher.class));

			if (selectDialog.getSelectedAction() == null) {
				throw new CancellationException();
			}

			// remember if we should auto-repeat the chosen action in the future
			if (selectDialog.getAutoRepeatCheckBox().isSelected() && selectDialog.getSelectedValue() != null) {
				getPersistentSelectionMemory().put(query, selectDialog.getSelectedValue());
			}

			// selected value or null if the dialog was canceled by the user
			return selectDialog.getSelectedValue();
		};

		synchronized (selectionMemory) {
			if (selectionMemory.containsKey(query)) {
				return selectionMemory.get(query);
			}

			// check persistent memory
			if (autodetection) {
				SearchResult persistentSelection = getPersistentSelectionMemory().get(query);
				if (persistentSelection != null) {
					return persistentSelection;
				}
			}

			// allow only one select dialog at a time
			SearchResult userSelection = showInputDialog(showSelectDialog);

			// remember selected value
			selectionMemory.put(query, userSelection);
			return userSelection;
		}
	}

	protected Collection<File> getFilesForQuery(Collection<File> files, String query) {
		Pattern pattern = Pattern.compile(query.isEmpty() ? ".+" : normalizePunctuation(query).replaceAll("\\W+", ".+"), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
		List<File> selection = files.stream().filter(f -> find(f.getPath(), pattern)).collect(toList());
		return selection.size() > 0 ? selection : files;
	}

	protected String getQueryInputMessage(String header, String message, Collection<File> files) throws Exception {
		List<File> selection = files.stream().sorted(comparing(File::length).reversed()).limit(4).sorted(HUMAN_NAME_ORDER).collect(toList());
		if (selection.isEmpty()) {
			return "";
		}

		StringBuilder html = new StringBuilder(512);
		html.append("<html>");
		if (header != null) {
			html.append(escapeHTML(header)).append("<br>");
		}

		TextColorizer colorizer = new TextColorizer("<nobr>• ", "</nobr><br>");
		for (File file : selection) {
			File path = getStructurePathTail(file);
			if (path == null) {
				path = getRelativePathTail(file, 3);
			}
			colorizer.colorizePath(html, path, true);
		}
		if (selection.size() < files.size()) {
			html.append("• ").append("…").append("<br>");
		}
		html.append("<br>");

		if (message != null) {
			html.append(escapeHTML(message));
		}
		html.append("</html>");
		return html.toString();
	}

	public List<Match<File, ?>> justFetchEpisodeList(SortOrder sortOrder, Locale locale, Component parent) throws Exception {
		// require user input
		List<String> input = showMultiValueInputDialog("Enter series name:", "", "Fetch Episode List", parent);

		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		if (input.size() > 0) {
			Collection<Episode> episodes = fetchEpisodeSet(emptyList(), input, sortOrder, locale, false, parent);
			for (Episode it : episodes) {
				matches.add(new Match<File, Episode>(null, it));
			}
		}
		return matches;
	}

}
