package net.filebot.similarity;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.web.EpisodeUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import net.filebot.media.SmartSeasonEpisodeMatcher;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.web.Episode;
import net.filebot.web.MultiEpisode;

public class EpisodeMatcher extends Matcher<File, Object> {

	public EpisodeMatcher(Collection<File> values, Collection<Episode> candidates, boolean strict) {
		// use strict matcher as to force a result from the final top similarity set
		super(values, candidates, strict, EpisodeMetrics.defaultSequence(false));
	}

	@Override
	protected void deepMatch(Collection<Match<File, Object>> possibleMatches, int level) throws InterruptedException {
		Map<File, List<Episode>> episodeSets = new IdentityHashMap<File, List<Episode>>();
		for (Match<File, Object> it : possibleMatches) {
			List<Episode> episodes = episodeSets.get(it.getValue());
			if (episodes == null) {
				episodes = new ArrayList<Episode>();
				episodeSets.put(it.getValue(), episodes);
			}
			episodes.add((Episode) it.getCandidate());
		}

		Map<File, Set<SxE>> episodeIdentifierSets = new IdentityHashMap<File, Set<SxE>>();
		for (Entry<File, List<Episode>> it : episodeSets.entrySet()) {
			Set<SxE> sxe = new HashSet<SxE>(it.getValue().size());
			for (Episode ep : it.getValue()) {
				if (ep.getSpecial() == null) {
					sxe.add(new SxE(ep.getSeason(), ep.getEpisode()));
				} else {
					sxe.add(new SxE(0, ep.getSpecial()));
				}
			}
			episodeIdentifierSets.put(it.getKey(), sxe);
		}

		boolean modified = false;
		for (Match<File, Object> it : possibleMatches) {
			File file = it.getValue();

			Set<Integer> uniqueFiles = normalizeIdentifierSet(parseEpisodeIdentifer(file));
			Set<Integer> uniqueEpisodes = normalizeIdentifierSet(episodeIdentifierSets.get(file));

			if (uniqueFiles.equals(uniqueEpisodes)) {
				List<Episode> episodes = episodeSets.get(file);

				if (episodes.size() > 1) {
					Episode[] episodeSequence = episodes.stream().sorted(episodeComparator()).distinct().toArray(Episode[]::new);

					if (isMultiEpisode(episodeSequence)) {
						MultiEpisode episode = new MultiEpisode(episodeSequence);
						disjointMatchCollection.add(new Match<File, Object>(file, episode));
						modified = true;
					}
				}
			}
		}

		if (modified) {
			removeCollected(possibleMatches);
		}

		super.deepMatch(possibleMatches, level);

	}

	private final SeasonEpisodeMatcher seasonEpisodeMatcher = new SmartSeasonEpisodeMatcher(SeasonEpisodeMatcher.LENIENT_SANITY, false);
	private final Map<File, Set<SxE>> transformCache = synchronizedMap(new HashMap<File, Set<SxE>>(64, 4));

	private Set<SxE> parseEpisodeIdentifer(File file) {
		Set<SxE> result = transformCache.get(file);
		if (result != null) {
			return result;
		}

		List<SxE> sxe = seasonEpisodeMatcher.match(file.getName());
		if (sxe != null) {
			result = new HashSet<SxE>(sxe);
		} else {
			result = emptySet();
		}

		transformCache.put(file, result);
		return result;
	}

	private Set<Integer> normalizeIdentifierSet(Set<SxE> numbers) {
		// check if any episode exceeds the episodes per season limit
		int limit = 100;
		for (SxE it : numbers) {
			while (it.season > 0 && it.episode >= limit) {
				limit *= 10;
			}
		}

		// SxE 1x01 => 101
		// Absolute 101 => 101
		Set<Integer> identifier = new HashSet<Integer>(numbers.size());
		for (SxE it : numbers) {
			if (it.season > 0 && it.episode > 0 && it.episode < limit) {
				identifier.add(it.season * limit + it.episode);
			} else if (it.season <= 0 && it.episode > 0) {
				identifier.add(it.episode);
			}
		}
		return identifier;
	}

	private boolean isMultiEpisode(Episode[] episodes) {
		if (episodes.length < 2) {
			return false;
		}

		// use getEpisode() or getSpecial() as number function
		Function<Episode, Integer> number = stream(episodes).allMatch(e -> e.getSpecial() == null) ? e -> e.getEpisode() : e -> e.getSpecial();

		// check episode sequence integrity
		Integer seqIndex = null;
		for (Episode it : episodes) {
			// any illegal episode object breaks the chain
			Integer i = number.apply(it);
			if (i == null) {
				return false;
			}

			// non-sequential next episode index breaks the chain (same episode is OK since DVD numbering allows for multiple episodes to share the same SxE numbers)
			if (seqIndex != null) {
				if (!(i.equals(seqIndex + 1) || i.equals(seqIndex))) {
					return false;
				}
			}

			seqIndex = i;
		}

		// check drill-down integrity
		return stream(episodes).skip(1).allMatch(e -> {
			return episodes[0].getSeriesName().equals(e.getSeriesName());
		});
	}

}
