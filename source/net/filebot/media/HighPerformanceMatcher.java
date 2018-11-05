package net.filebot.media;

import static java.util.stream.Collectors.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.RegularExpressions.*;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.filebot.similarity.CommonSequenceMatcher;
import net.filebot.web.Movie;
import net.filebot.web.SearchResult;

/**
 * Fast name matcher used for matching a file to or more movies (out of a list of ~50k in milliseconds)
 */
class HighPerformanceMatcher extends CommonSequenceMatcher {

	private static final Collator collator = getLenientCollator(Locale.ENGLISH);

	public static CollationKey[] prepare(String sequence) {
		String[] words = SPACE.split(sequence);
		CollationKey[] keys = new CollationKey[words.length];
		for (int i = 0; i < words.length; i++) {
			keys[i] = collator.getCollationKey(words[i]);
		}
		return keys;
	}

	public static List<CollationKey[]> prepare(Collection<String> sequences) {
		return sequences.stream().filter(Objects::nonNull).map(s -> {
			return prepare(normalizePunctuation(s));
		}).collect(toList());
	}

	public static List<IndexEntry<Movie>> prepare(Movie m) {
		List<String> effectiveNamesWithoutYear = m.getEffectiveNamesWithoutYear();
		List<String> effectiveNames = m.getEffectiveNames();
		List<IndexEntry<Movie>> index = new ArrayList<IndexEntry<Movie>>(effectiveNames.size());

		for (int i = 0; i < effectiveNames.size(); i++) {
			String lenientName = normalizePunctuation(effectiveNamesWithoutYear.get(i));
			String strictName = normalizePunctuation(effectiveNames.get(i));
			index.add(new IndexEntry<Movie>(m, lenientName, strictName));
		}
		return index;
	}

	public static List<IndexEntry<SearchResult>> prepare(SearchResult r) {
		List<String> effectiveNames = r.getEffectiveNames();
		List<IndexEntry<SearchResult>> index = new ArrayList<IndexEntry<SearchResult>>(effectiveNames.size());

		for (int i = 0; i < effectiveNames.size(); i++) {
			String lenientName = normalizePunctuation(effectiveNames.get(i));
			index.add(new IndexEntry<SearchResult>(r, lenientName, null));
		}
		return index;
	}

	public HighPerformanceMatcher(int maxStartIndex) {
		super(collator, maxStartIndex, true);
	}

	@Override
	public CollationKey[] split(String sequence) {
		throw new UnsupportedOperationException("requires ahead-of-time collation");
	}
}