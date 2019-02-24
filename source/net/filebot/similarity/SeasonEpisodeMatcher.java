package net.filebot.similarity;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class SeasonEpisodeMatcher {

	public static final SeasonEpisodeFilter LENIENT_SANITY = new SeasonEpisodeFilter(99, 999, 9999, 1970, 2100);
	public static final SeasonEpisodeFilter DEFAULT_SANITY = new SeasonEpisodeFilter(50, 50, 1000, 1970, 2100);
	public static final SeasonEpisodeFilter STRICT_SANITY = new SeasonEpisodeFilter(10, 30, -1, -1, -1);

	private SeasonEpisodeParser[] patterns;
	private Pattern seasonPattern;

	public SeasonEpisodeMatcher(SeasonEpisodeFilter sanity, boolean strict) {
		// define variables
		SeasonEpisodePattern Season_00_Episode_00, S00E00SEQ, S00E00, SxE1_SxE2, SxE, Dot101, E01E02SEQ, EP0, Num101_TOKEN, E1of2, Num101_SUBSTRING;

		// match patterns like Season 01 Episode 02, ...
		Season_00_Episode_00 = new SeasonEpisodePattern(null, "(?<!\\p{Alnum})(?i:season|series)[^\\p{Alnum}]{0,3}(\\d{1,4})[^\\p{Alnum}]{0,3}(?i:episode)[^\\p{Alnum}]{0,3}((\\d{1,3}(\\D|$))+)[^\\p{Alnum}]{0,3}(?!\\p{Digit})", m -> {
			return range(m.group(1), m.group(2));
		});

		// match patterns like S01E01-E05
		S00E00SEQ = new SeasonEpisodePattern(null, "(?<!\\p{Alnum}|[-])[Ss](\\d{1,2}|\\d{4})[Ee](\\d{2,3})[-][Ee](\\d{2,3})(?!\\p{Alnum}|[-])", m -> {
			return range(m.group(1), m.group(2), m.group(3));
		});

		// match patterns like S01E01, s01e02, ... [s01]_[e02], s01.e02, s01e02a, s2010e01 ... s01e01-02-03-04, [s01]_[e01-02-03-04] ...
		S00E00 = new SeasonEpisodePattern(null, "(?<!\\p{Digit})[Ss](\\d{1,2}|\\d{4})[^\\p{Alnum}]{0,3}(?i:ep|e|p|-)(((?<=[^._ ])[Ee]?[Pp]?\\d{1,3}(\\D|$))+)", m -> {
			return multi(m.group(1), m.group(2));
		});

		// match patterns 1x01-1x02, ...
		SxE1_SxE2 = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum})(\\d{1,2}x\\d{2}([-._ ]\\d{1,2}x\\d{2})+)(?!\\p{Digit})", m -> {
			return pairs(m.group());
		});

		// match patterns like 1x01, 1.02, ..., 1x01a, 10x01, 10.02, ... 1x01-02-03-04, 1x01x02x03x04 ...
		SxE = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum})(\\d{1,2})[xe](((?<=[^._ ])\\d{2,3}(\\D|$))+)", m -> {
			return multi(m.group(1), m.group(2));
		});

		// match patterns 1.02, ..., 10.02, ...
		Dot101 = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum}|\\d{4}[.])(\\d{1,2})[.](((?<=[^._ ])\\d{2}(\\D|$))+)", m -> {
			return multi(m.group(1), m.group(2));
		});

		// match patterns like 101-105
		E01E02SEQ = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum}|[-])(\\d{2,3})[-](\\d{2,3})(?!\\p{Alnum}|[-])", m -> {
			return range(null, m.group(1), m.group(2));
		});

		// match patterns like ep1, ep.1, ...
		EP0 = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum})(\\d{2}|\\d{4})?[\\P{Alnum}]{0,3}(((?i:e|ep|episode|p|part)[\\P{Alnum}]{0,3}\\d{1,3})+)(?!\\p{Digit})", m -> {
			return multi(m.group(1), m.group(2));
		});

		// match patterns like 01, 102, 1003, 10102 (enclosed in separators)
		Num101_TOKEN = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum})([0-2]?\\d?)(\\d{2})(\\d{2})?(?!\\p{Alnum})", m -> {
			return numbers(m.group(1), streamCapturingGroups(m).skip(1).toArray(String[]::new));
		});

		// match patterns like "1 of 2" as Episode 1
		E1of2 = new SeasonEpisodePattern(sanity, "(?<!\\p{Alnum})(\\d{1,2})[^._ ]?(?i:of)[^._ ]?(\\d{1,2})(?!\\p{Digit})", m -> {
			return single(null, m.group(1));
		});

		// (last-resort) match patterns like 101, 102 (and greedily just grab the first)
		Num101_SUBSTRING = new SeasonEpisodePattern(STRICT_SANITY, "(?<!\\p{Digit})(\\d{1})(\\d{2})(?!\\p{Digit})(.*)", m -> {
			return single(m.group(1), m.group(2));
		});

		// only use S00E00 and SxE pattern in strict mode
		if (strict) {
			patterns = new SeasonEpisodeParser[] { Season_00_Episode_00, S00E00SEQ, S00E00, SxE1_SxE2, SxE, Dot101 };
		} else {
			patterns = new SeasonEpisodeParser[] { Season_00_Episode_00, S00E00SEQ, S00E00, SxE1_SxE2, SxE, Dot101, E01E02SEQ, new SeasonEpisodeUnion(EP0, Num101_TOKEN, E1of2), Num101_SUBSTRING };
		}

		// season folder pattern for complementing partial sxe info from filename
		seasonPattern = compile("Season[-._ ]?(\\d{1,2})", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
	}

	protected List<SxE> single(String season, String episode) {
		return singletonList(new SxE(season, episode));
	}

	protected List<SxE> multi(String season, String... episodes) {
		Integer s = matchInteger(season);
		return stream(episodes).flatMap(e -> matchIntegers(e).stream()).map(e -> new SxE(s, e)).collect(toList());
	}

	protected List<SxE> range(String season, String... episodes) {
		IntSummaryStatistics stats = stream(episodes).flatMap(s -> matchIntegers(s).stream()).mapToInt(i -> i).summaryStatistics();

		// range patterns without season are more prone to false positives, so we need to do some extra sanity checks (e.g. Episode 01-50 is probably not a multi-episode but some sort of season pack)
		if (season == null && stats.getMax() - stats.getMin() >= 9) {
			return emptyList();
		}

		Integer s = matchInteger(season);
		return IntStream.rangeClosed(stats.getMin(), stats.getMax()).boxed().map(e -> new SxE(s, e)).collect(toList());
	}

	protected List<SxE> pairs(String text) {
		List<SxE> matches = new ArrayList<SxE>(2);

		// SxE-SxE-SxE
		String[] numbers = NON_DIGIT.split(text);
		for (int i = 0; i < numbers.length; i += 2) {
			matches.add(new SxE(numbers[i], numbers[i + 1]));
		}

		return matches;
	}

	protected List<SxE> numbers(String head, String... tail) {
		List<SxE> matches = new ArrayList<SxE>(2);

		// interpret match as season and episode, but ignore 001 => 0x01 Season 0 matches
		for (String t : tail) {
			SxE sxe = new SxE(head, t);
			if (sxe.season > 0) {
				matches.add(sxe);
			}
		}

		// interpret match both ways, as SxE match as well as episode number only match if it's not an double episode
		if (tail.length == 1) {
			SxE absolute = new SxE(null, head + tail[0]);
			if (!matches.contains(absolute)) {
				matches.add(absolute);
			}
		}

		// return both matches, unless they are one and the same
		return matches;
	}

	/**
	 * Try to get season and episode numbers for the given string.
	 *
	 * @param name
	 *            match this string against the a set of know patterns
	 * @return the matches returned by the first pattern that returns any matches for this string, or null if no pattern returned any matches
	 */
	public List<SxE> match(CharSequence name) {
		for (SeasonEpisodeParser pattern : patterns) {
			List<SxE> match = pattern.match(name);

			if (!match.isEmpty()) {
				// current pattern did match
				return match;
			}
		}
		return null;
	}

	public List<SxE> match(File file) {
		// take folder name into consideration as much as file name but put priority on file name
		List<String> tail = tokenizeTail(file);

		for (SeasonEpisodeParser pattern : patterns) {
			for (int t = 0; t < tail.size(); t++) {
				List<SxE> match = pattern.match(tail.get(t));

				if (!match.isEmpty()) {
					// current pattern did match
					for (int i = 0; i < match.size(); i++) {
						if (match.get(i).season < 0 && t < tail.size() - 1) {
							Matcher sm = seasonPattern.matcher(tail.get(t + 1));
							if (sm.find()) {
								match.set(i, new SxE(Integer.parseInt(sm.group(1)), match.get(i).episode));
							}
						}
					}
					return match;
				}
			}
		}
		return null;
	}

	protected List<String> tokenizeTail(File file) {
		List<String> tail = new ArrayList<String>(2);
		for (File f : listPathTail(file, 2, true)) {
			tail.add(getName(f));
		}
		return tail;
	}

	public int find(CharSequence name, int fromIndex) {
		for (SeasonEpisodeParser pattern : patterns) {
			int index = pattern.find(name, fromIndex);

			if (index >= 0) {
				// current pattern did match
				return index;
			}
		}

		return -1;
	}

	public String head(String name) {
		int seasonEpisodePosition = find(name, 0);
		if (seasonEpisodePosition > 0) {
			return name.substring(0, seasonEpisodePosition).trim();
		}
		return null;
	}

	public static class SxE implements Comparable<SxE> {

		public static final int UNDEFINED = -1;

		public final int season;
		public final int episode;

		public SxE(Integer season, Integer episode) {
			this.season = season != null ? season : UNDEFINED;
			this.episode = episode != null ? episode : UNDEFINED;
		}

		public SxE(String season, String episode) {
			this.season = parse(season);
			this.episode = parse(episode);
		}

		protected int parse(String number) {
			try {
				return Integer.parseInt(number);
			} catch (Exception e) {
				return UNDEFINED;
			}
		}

		@Override
		public boolean equals(Object object) {
			if (object instanceof SxE) {
				SxE other = (SxE) object;
				return this.season == other.season && this.episode == other.episode;
			}

			return false;
		}

		@Override
		public int compareTo(SxE other) {
			return season < other.season ? -1 : season != other.season ? 1 : episode < other.episode ? -1 : episode != other.episode ? 1 : 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(season, episode);
		}

		@Override
		public String toString() {
			return season >= 0 ? String.format("%dx%02d", season, episode) : String.format("%02d", episode);
		}
	}

	public static class SeasonEpisodeFilter {

		public final int seasonLimit;
		public final int seasonEpisodeLimit;
		public final int absoluteEpisodeLimit;
		public final int seasonYearBegin;
		public final int seasonYearEnd;

		public SeasonEpisodeFilter(int seasonLimit, int seasonEpisodeLimit, int absoluteEpisodeLimit, int seasonYearBegin, int seasonYearEnd) {
			this.seasonLimit = seasonLimit;
			this.seasonEpisodeLimit = seasonEpisodeLimit;
			this.absoluteEpisodeLimit = absoluteEpisodeLimit;
			this.seasonYearBegin = seasonYearBegin;
			this.seasonYearEnd = seasonYearEnd;
		}

		boolean filter(SxE sxe) {
			return (sxe.season >= 0 && (sxe.season < seasonLimit || (sxe.season > seasonYearBegin && sxe.season < seasonYearEnd)) && sxe.episode < seasonEpisodeLimit) || (sxe.season < 0 && sxe.episode < absoluteEpisodeLimit);
		}

		public boolean filter(SxE value, List<SxE> sequence) {
			return filter(value) && sequence.stream().filter(other -> {
				return (value.season == SxE.UNDEFINED) == (other.season == SxE.UNDEFINED) && other.compareTo(value) > 0;
			}).count() == 0;
		}
	}

	public static interface SeasonEpisodeParser {

		public abstract List<SxE> match(CharSequence name);

		public abstract int find(CharSequence name, int fromIndex);
	}

	public static class SeasonEpisodePattern implements SeasonEpisodeParser {

		protected Pattern pattern;
		protected Function<MatchResult, List<SxE>> process;

		protected SeasonEpisodeFilter sanity;

		public SeasonEpisodePattern(SeasonEpisodeFilter sanity, String pattern) {
			this(sanity, pattern, m -> singletonList(new SxE(m.group(1), m.group(2))));
		}

		public SeasonEpisodePattern(SeasonEpisodeFilter sanity, String pattern, Function<MatchResult, List<SxE>> process) {
			this.pattern = Pattern.compile(pattern);
			this.process = process;
			this.sanity = sanity;
		}

		public Matcher matcher(CharSequence name) {
			return pattern.matcher(name);
		}

		@Override
		public List<SxE> match(CharSequence name) {
			// name will probably contain no more than two matches
			List<SxE> matches = new ArrayList<SxE>(2);

			Matcher matcher = matcher(name);

			while (matcher.find()) {
				for (SxE value : process.apply(matcher)) {
					if (sanity == null || sanity.filter(value, matches)) {
						matches.add(value);
					}
				}
			}

			return matches;
		}

		@Override
		public int find(CharSequence name, int fromIndex) {
			Matcher matcher = matcher(name).region(fromIndex, name.length());

			while (matcher.find()) {
				for (SxE value : process.apply(matcher)) {
					if (sanity == null || sanity.filter(value)) {
						return matcher.start();
					}
				}
			}

			return -1;
		}

		@Override
		public String toString() {
			return pattern.pattern();
		}
	}

	public static class SeasonEpisodeUnion implements SeasonEpisodeParser {

		private final SeasonEpisodeParser[] parsers;

		public SeasonEpisodeUnion(SeasonEpisodeParser... parsers) {
			this.parsers = parsers;
		}

		@Override
		public List<SxE> match(CharSequence name) {
			Set<SxE> matches = new LinkedHashSet<SxE>();
			for (SeasonEpisodeParser it : parsers) {
				matches.addAll(it.match(name));
			}

			return new ArrayList<SxE>(matches);
		}

		@Override
		public int find(CharSequence name, int fromIndex) {
			int min = -1;
			for (SeasonEpisodeParser it : parsers) {
				int pos = it.find(name, fromIndex);
				if (pos >= 0 && pos > min) {
					min = pos;
				}
			}

			return min;
		}
	}

}
