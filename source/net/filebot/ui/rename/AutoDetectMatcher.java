
package net.filebot.ui.rename;

import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.WebServices.*;
import static net.filebot.util.ExceptionUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Stream;

import net.filebot.media.AutoDetection;
import net.filebot.media.AutoDetection.Group;
import net.filebot.media.AutoDetection.Type;
import net.filebot.similarity.Match;
import net.filebot.web.SortOrder;

class AutoDetectMatcher implements AutoCompleteMatcher {

	private AutoCompleteMatcher movie = new MovieMatcher(TheMovieDB);
	private AutoCompleteMatcher episode = new EpisodeListMatcher(TheTVDB, false);
	private AutoCompleteMatcher anime = new EpisodeListMatcher(AniDB, true);
	private AutoCompleteMatcher music = new MusicMatcher(MediaInfoID3, AcoustID);

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		Map<Group, Set<File>> groups = new AutoDetection(files, false, locale).group();

		// can't use parallel stream because default fork/join pool doesn't play well with the security manager
		ExecutorService workerThreadPool = Executors.newFixedThreadPool(getPreferredThreadPoolSize());
		try {
			// match groups in parallel
			List<Future<List<Match<File, ?>>>> matches = groups.entrySet().stream().filter(it -> {
				return it.getKey().types().length == 1; // unambiguous group
			}).map(it -> {
				return workerThreadPool.submit(() -> match(it.getKey(), it.getValue(), strict, order, locale, autodetection, parent));
			}).collect(toList());

			// collect results
			return matches.stream().flatMap(it -> {
				try {
					return it.get().stream();
				} catch (Exception e) {
					// CancellationException is expected
					if (findCause(e, CancellationException.class) == null) {
						log.log(Level.WARNING, e, cause("Failed to match group", e));
					}
					return Stream.empty();
				}
			}).sorted(comparing(Match::getValue, OriginalOrder.of(files))).collect(toList());
		} finally {
			workerThreadPool.shutdownNow();
		}
	}

	private List<Match<File, ?>> match(Group group, Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		AutoCompleteMatcher m = getMatcher(group);
		if (m != null) {
			return m.match(files, strict, order, locale, autodetection, parent);
		}
		return emptyList();
	}

	private AutoCompleteMatcher getMatcher(Group group) {
		for (Type key : group.types()) {
			switch (key) {
			case Movie:
				return movie;
			case Series:
				return episode;
			case Anime:
				return anime;
			case Music:
				return music;
			}
		}
		return null;
	}

}
