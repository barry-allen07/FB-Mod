package net.filebot.similarity;

import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static net.filebot.similarity.CommonSequenceMatcher.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.text.CollationKey;
import java.text.Collator;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.filebot.media.SmartSeasonEpisodeMatcher;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.util.FileUtilities;

public class SeriesNameMatcher {

	protected final SimilarityMetric metric;
	protected final SeasonEpisodeMatcher seasonEpisodeMatcher;
	protected final DateMatcher dateMatcher;
	protected final CommonSequenceMatcher commonSequenceMatcher;

	public SeriesNameMatcher(boolean strict) {
		this(new NameSimilarityMetric(), getLenientCollator(Locale.ENGLISH), new SmartSeasonEpisodeMatcher(SeasonEpisodeMatcher.DEFAULT_SANITY, strict), new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.ENGLISH));
	}

	public SeriesNameMatcher(SeasonEpisodeMatcher seasonEpisodeMatcher, DateMatcher dateMatcher) {
		this(new NameSimilarityMetric(), getLenientCollator(Locale.ENGLISH), seasonEpisodeMatcher, dateMatcher);
	}

	public SeriesNameMatcher(SimilarityMetric metric, Collator collator, SeasonEpisodeMatcher seasonEpisodeMatcher, DateMatcher dateMatcher) {
		this.metric = metric;
		this.seasonEpisodeMatcher = seasonEpisodeMatcher;
		this.dateMatcher = dateMatcher;

		this.commonSequenceMatcher = new CommonSequenceMatcher(collator, 3, true) {

			@Override
			public CollationKey[] split(String sequence) {
				return super.split(normalize(sequence));
			}
		};
	}

	public Collection<String> matchAll(File[] files) {
		SeriesNameCollection seriesNames = new SeriesNameCollection();

		// group files by parent folder
		for (Entry<File, String[]> entry : mapNamesByFolder(files).entrySet()) {
			String parent = entry.getKey().getName();
			String[] names = entry.getValue();

			for (String nameMatch : matchAll(names)) {
				String commonMatch = commonSequenceMatcher.matchFirstCommonSequence(nameMatch, parent);
				float similarity = commonMatch == null ? 0 : metric.getSimilarity(commonMatch, nameMatch);

				// prefer common match, but only if it's very similar to the original match
				seriesNames.add(similarity > 0.7 ? commonMatch : nameMatch);
			}
		}

		return seriesNames;
	}

	public Collection<String> matchAll(String[] names) {
		SeriesNameCollection seriesNames = new SeriesNameCollection();

		// allow matching of a small number of episodes, by setting threshold = length if length < 5
		int threshold = Math.min(names.length, 5);

		// match common word sequences (likely series names)
		SeriesNameCollection whitelist = new SeriesNameCollection();

		// focus chars before the SxE / Date pattern when matching by common word sequence
		String[] focus = Arrays.copyOf(names, names.length);
		for (int i = 0; i < focus.length; i++) {
			String beforeSxE = seasonEpisodeMatcher.head(focus[i]);
			if (beforeSxE != null && beforeSxE.length() > 0) {
				focus[i] = beforeSxE;
			} else {
				int datePos = dateMatcher.find(focus[i], 0);
				if (datePos >= 0) {
					focus[i] = focus[i].substring(0, datePos);
				}
			}
		}
		whitelist.addAll(deepMatchAll(focus, threshold));

		// 1. use pattern matching
		seriesNames.addAll(flatMatchAll(names, compile(join(whitelist, "|"), CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS), threshold, false));

		// 2. use common word sequences
		seriesNames.addAll(whitelist);

		return seriesNames;
	}

	/**
	 * Try to match and verify all series names using known season episode patterns.
	 *
	 * @param names
	 *            episode names
	 * @return series names that have been matched one or multiple times depending on the threshold
	 */
	private Collection<String> flatMatchAll(String[] names, Pattern prefixPattern, int threshold, boolean strict) {
		Comparator<String> wordComparator = (Comparator) commonSequenceMatcher.getCollator();
		ThresholdCollection<String> thresholdCollection = new ThresholdCollection<String>(threshold, wordComparator);

		for (String name : names) {
			// use normalized name
			name = normalize(name);

			Matcher prefix = prefixPattern.matcher(name);
			int prefixEnd = prefix.find() ? prefix.end() : 0;

			int sxePosition = seasonEpisodeMatcher.find(name, prefixEnd);
			if (sxePosition > 0) {
				String hit = name.substring(0, sxePosition).trim();
				List<SxE> sxe = seasonEpisodeMatcher.match(name.substring(sxePosition));

				if (!strict && sxe.size() == 1 && sxe.get(0).season >= 0) {
					// bypass threshold if hit is likely to be genuine
					thresholdCollection.addDirect(hit);
				} else {
					// require multiple matches, if hit might be a false match
					thresholdCollection.add(hit);
				}
			} else {
				// try date pattern as fallback
				int datePosition = dateMatcher.find(name, prefixEnd);
				if (datePosition > 0) {
					thresholdCollection.addDirect(name.substring(0, datePosition).trim());
				}
			}

		}

		return thresholdCollection;
	}

	/**
	 * Try to match all common word sequences in the given list.
	 *
	 * @param names
	 *            list of episode names
	 * @return all common word sequences that have been found
	 */
	private Collection<String> deepMatchAll(String[] names, int threshold) {
		// can't use common word sequence matching for less than 2 names
		if (names.length < 2 || names.length < threshold) {
			return emptySet();
		}

		String common = commonSequenceMatcher.matchFirstCommonSequence(names);

		if (common != null) {
			// common word sequence found
			return singleton(common);
		}

		// recursive divide and conquer
		List<String> results = new ArrayList<String>();

		// split list in two and try to match common word sequence on those
		results.addAll(deepMatchAll(Arrays.copyOfRange(names, 0, names.length / 2), threshold));
		results.addAll(deepMatchAll(Arrays.copyOfRange(names, names.length / 2, names.length), threshold));

		return results;
	}

	/**
	 * Try to match a series name from the given episode name using known season episode patterns.
	 *
	 * @param name
	 *            episode name
	 * @return a substring of the given name that ends before the first occurrence of a season episode pattern, or null if there is no such pattern
	 */
	public String matchByEpisodeIdentifier(String name) {
		// series name ends at the first season episode pattern
		String seriesName = seasonEpisodeMatcher.head(name);
		if (seriesName != null && seriesName.length() > 0) {
			return seriesName;
		}

		int datePosition = dateMatcher.find(name, 0);
		if (datePosition > 0) {
			// series name ends at the first season episode pattern
			return name.substring(0, datePosition);
		}

		return null;
	}

	public String matchBySeparator(String name) {
		Pattern separator = Pattern.compile("[\\s]+[-]+[\\s]+", Pattern.UNICODE_CHARACTER_CLASS);

		Matcher matcher = separator.matcher(name);
		if (matcher.find() && matcher.start() > 0) {
			return normalizePunctuation(name.substring(0, matcher.start()));
		}

		return null;
	}

	/**
	 * Try to match a series name from the first common word sequence.
	 *
	 * @param names
	 *            various episode names (at least two)
	 * @return a word sequence all episode names have in common, or null
	 * @throws IllegalArgumentException
	 *             if less than 2 episode names are given
	 */
	public String matchByFirstCommonWordSequence(String... names) {
		if (names.length < 2) {
			throw new IllegalArgumentException("Can't match common sequence from less than two names");
		}

		return commonSequenceMatcher.matchFirstCommonSequence(names);
	}

	protected String normalize(String name) {
		// remove group names and checksums, any [...] or (...) and remove/normalize special characters
		return normalizePunctuation(normalizeBrackets(name));
	}

	protected <T> T[] firstCommonSequence(T[] seq1, T[] seq2, int maxStartIndex, Comparator<T> equalsComparator) {
		for (int i = 0; i < seq1.length && i <= maxStartIndex; i++) {
			for (int j = 0; j < seq2.length && j <= maxStartIndex; j++) {
				// common sequence length
				int len = 0;

				// iterate over common sequence
				while ((i + len < seq1.length) && (j + len < seq2.length) && (equalsComparator.compare(seq1[i + len], seq2[j + len]) == 0)) {
					len++;
				}

				// check if a common sequence was found
				if (len > 0) {
					if (i == 0 && len == seq1.length)
						return seq1;

					return Arrays.copyOfRange(seq1, i, i + len);
				}
			}
		}

		// no intersection at all
		return null;
	}

	private Map<File, String[]> mapNamesByFolder(File... files) {
		Map<File, List<File>> filesByFolder = new LinkedHashMap<File, List<File>>();

		for (File file : files) {
			File folder = file.getParentFile();

			List<File> list = filesByFolder.get(folder);

			if (list == null) {
				list = new ArrayList<File>();
				filesByFolder.put(folder, list);
			}

			list.add(file);
		}

		// convert folder->files map to folder->names map
		Map<File, String[]> namesByFolder = new LinkedHashMap<File, String[]>();

		for (Entry<File, List<File>> entry : filesByFolder.entrySet()) {
			namesByFolder.put(entry.getKey(), names(entry.getValue()));
		}

		return namesByFolder;
	}

	protected String[] names(Collection<File> files) {
		String[] names = new String[files.size()];

		int i = 0;

		// fill array
		for (File file : files) {
			names[i++] = FileUtilities.getName(file);
		}

		return names;
	}

	protected static class SeriesNameCollection extends AbstractCollection<String> {

		private final Map<String, String> data = new LinkedHashMap<String, String>();

		@Override
		public boolean add(String value) {
			value = value.trim();

			// require series name to have at least two characters
			if (value.length() < 2) {
				return false;
			}

			String current = data.get(key(value));

			// prefer strings with similar upper/lower case ratio (e.g. prefer Roswell over roswell)
			if (current == null || firstCharacterCaseBalance(current) < firstCharacterCaseBalance(value)) {
				data.put(key(value), value);
				return true;
			}

			return false;
		}

		protected String key(Object value) {
			return value.toString().toLowerCase();
		}

		protected float firstCharacterCaseBalance(String s) {
			int upper = 0;
			int lower = 0;

			Scanner scanner = new Scanner(s); // Scanner uses a white space delimiter by default

			while (scanner.hasNext()) {
				char c = scanner.next().charAt(0);

				if (Character.isLowerCase(c))
					lower++;
				else if (Character.isUpperCase(c))
					upper++;
			}

			// give upper case characters a slight boost over lower case characters
			return (lower + (upper * 1.01f)) / Math.abs(lower - upper);
		}

		@Override
		public boolean contains(Object value) {
			return data.containsKey(key(value));
		}

		@Override
		public Iterator<String> iterator() {
			return data.values().iterator();
		}

		@Override
		public int size() {
			return data.size();
		}

	}

	protected static class ThresholdCollection<E> extends AbstractCollection<E> {

		private final Collection<E> heaven;
		private final Map<E, Collection<E>> limbo;

		private final int threshold;

		public ThresholdCollection(int threshold, Comparator<E> equalityComparator) {
			this.heaven = new ArrayList<E>();
			this.limbo = new TreeMap<E, Collection<E>>(equalityComparator);
			this.threshold = threshold;
		}

		@Override
		public boolean add(E value) {
			Collection<E> buffer = limbo.get(value);

			if (buffer == null) {
				// initialize buffer
				buffer = new ArrayList<E>(threshold);
				limbo.put(value, buffer);
			}

			if (buffer == heaven) {
				// threshold reached
				heaven.add(value);
				return true;
			}

			// add element to buffer
			buffer.add(value);

			// check if threshold has been reached
			if (buffer.size() >= threshold) {
				heaven.addAll(buffer);

				// replace buffer with heaven
				limbo.put(value, heaven);
				return true;
			}

			return false;
		};

		public boolean addDirect(E element) {
			return heaven.add(element);
		}

		@Override
		public Iterator<E> iterator() {
			return heaven.iterator();
		}

		@Override
		public int size() {
			return heaven.size();
		}

	}

}
