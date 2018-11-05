package net.filebot.similarity;

import static java.util.stream.Collectors.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.filebot.web.SimpleDate;
import one.util.streamex.StreamEx;

public class DateMatcher {

	public static final DateFilter DEFAULT_SANITY = new DateFilter(1930, 2050);

	private final DatePattern[] patterns;

	public DateMatcher(DateFilter sanity, Locale... locale) {
		// generate default date format patterns
		String[] format = new String[7];

		// match yyyy-mm-dd patterns like 2010-10-24, 2009/6/1, etc
		format[0] = "y M d";

		// match dd-mm-yyyy patterns like 1.1.2010, 01/06/2010, etc
		format[1] = "d M y";

		// match yyyy.MMMMM.dd patterns like 2015.October.05
		format[2] = "y MMMM d";

		// match yyyy.MMM.dd patterns like 2015.Oct.6
		format[3] = "y MMM d";

		// match dd.MMMMM.yyyy patterns like 25 July 2014
		format[4] = "d MMMM y";

		// match dd.MMM.yyyy patterns like 8 Sep 2015
		format[5] = "d MMM y";

		// match yyyymmdd patterns like 20140408
		format[6] = "yyyyMMdd";

		this.patterns = compile(format, sanity, locale);
	}

	protected DatePattern[] compile(String[] pattern, DateFilter sanity, Locale... locale) {
		return StreamEx.of(pattern).flatMap(dateFormat -> {
			return StreamEx.of(locale).distinct(Locale::getLanguage).map(formatLocale -> {
				String regex = StreamEx.split(dateFormat, DateFormatPattern.DELIMITER).map(g -> getPatternGroup(g, formatLocale)).joining("\\D", "(?<!\\p{Alnum})", "(?!\\p{Alnum})");
				return new DateFormatPattern(regex, dateFormat, formatLocale, sanity);
			}).distinct(DateFormatPattern::toString);
		}).toArray(DateFormatPattern[]::new);
	}

	protected String getPatternGroup(String token, Locale locale) {
		switch (token) {
		case "y":
			return "(\\d{4})";
		case "M":
			return "(\\d{1,2})";
		case "d":
			return "(\\d{1,2})";
		case "yyyyMMdd":
			return "(\\d{8})";
		case "MMMM":
			return getMonthNamePatternGroup(TextStyle.FULL, locale);
		case "MMM":
			return getMonthNamePatternGroup(TextStyle.SHORT, locale);
		default:
			throw new IllegalArgumentException(token);
		}
	}

	protected String getMonthNamePatternGroup(TextStyle style, Locale locale) {
		return StreamEx.of(Month.values()).map(m -> m.getDisplayName(style, locale)).map(Pattern::quote).joining("|", "(", ")");
	}

	public SimpleDate match(CharSequence seq) {
		for (DatePattern pattern : patterns) {
			SimpleDate match = pattern.match(seq);

			if (match != null) {
				return match;
			}
		}
		return null;
	}

	public int find(CharSequence seq, int fromIndex) {
		for (DatePattern pattern : patterns) {
			int pos = pattern.find(seq, fromIndex);

			if (pos >= 0) {
				return pos;
			}
		}
		return -1;
	}

	public SimpleDate match(File file) {
		for (String name : tokenizeTail(file)) {
			for (DatePattern pattern : patterns) {
				SimpleDate match = pattern.match(name);

				if (match != null) {
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

	public static interface DatePattern {

		public SimpleDate match(CharSequence seq);

		public int find(CharSequence seq, int fromIndex);

	}

	public static class DateFormatPattern implements DatePattern {

		public static final String DELIMITER = " ";

		public final Pattern pattern;
		public final DateTimeFormatter format;
		public final DateFilter sanity;

		public DateFormatPattern(String pattern, String format, Locale locale, DateFilter sanity) {
			this.pattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
			this.format = DateTimeFormatter.ofPattern(format, locale);
			this.sanity = sanity;
		}

		protected SimpleDate process(MatchResult match) {
			try {
				String dateString = streamCapturingGroups(match).collect(joining(DELIMITER));
				LocalDate date = LocalDate.parse(dateString, format);

				if (sanity == null || sanity.test(date)) {
					return new SimpleDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
				}
			} catch (DateTimeParseException e) {
				// date is invalid
			}
			return null;
		}

		@Override
		public SimpleDate match(CharSequence seq) {
			Matcher matcher = pattern.matcher(seq);

			if (matcher.find()) {
				return process(matcher);
			}
			return null;
		}

		@Override
		public int find(CharSequence seq, int fromIndex) {
			Matcher matcher = pattern.matcher(seq).region(fromIndex, seq.length());

			if (matcher.find()) {
				if (process(matcher) != null) {
					return matcher.start();
				}
			}
			return -1;
		}

		@Override
		public String toString() {
			return pattern.pattern();
		}

	}

	public static class DateFilter implements Predicate<LocalDate> {

		public final LocalDate min;
		public final LocalDate max;

		private final int minYear;
		private final int maxYear;

		public DateFilter(LocalDate min, LocalDate max) {
			this.min = min;
			this.max = max;
			this.minYear = min.getYear();
			this.maxYear = max.getYear();
		}

		public DateFilter(int minYear, int maxYear) {
			this.min = LocalDate.of(minYear, Month.JANUARY, 1);
			this.max = LocalDate.of(maxYear, Month.JANUARY, 1);
			this.minYear = minYear;
			this.maxYear = maxYear;
		}

		@Override
		public boolean test(LocalDate date) {
			return date.isAfter(min) && date.isBefore(max);
		}

		public boolean acceptYear(int year) {
			return minYear <= year && year <= maxYear;
		}

		public boolean acceptDate(int year, int month, int day) {
			return acceptYear(year) && test(LocalDate.of(year, month, day));
		}

	}

}
