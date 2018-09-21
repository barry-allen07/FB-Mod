package net.filebot.format;

import static java.util.Arrays.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.WebServices.*;
import static net.filebot.format.ExpressionFormatFunctions.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import com.ibm.icu.text.Transliterator;

import groovy.lang.Closure;
import net.filebot.Language;
import net.filebot.similarity.Normalization;
import net.filebot.util.FileUtilities;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeInfo;
import net.filebot.web.Movie;
import net.filebot.web.Person;
import net.filebot.web.SeriesInfo;
import net.filebot.web.SimpleDate;

public class ExpressionFormatMethods {

	/**
	 * Convenience methods for String.toLowerCase() and String.toUpperCase()
	 */
	public static String lower(String self) {
		return self.toLowerCase();
	}

	public static String upper(String self) {
		return self.toUpperCase();
	}

	/**
	 * Pad strings or numbers with given characters ('0' by default).
	 *
	 * e.g. "1" -> "01"
	 */
	public static String pad(String self, int length, String padding) {
		while (self.length() < length) {
			self = padding + self;
		}
		return self;
	}

	public static String pad(String self, int length) {
		return pad(self, length, "0");
	}

	public static String pad(Number self, int length) {
		return pad(self.toString(), length, "0");
	}

	public static double round(Number self, int precision) {
		return DefaultGroovyMethods.round(self.doubleValue(), precision);
	}

	/**
	 * Return a substring matching the given pattern or break.
	 */
	public static String match(String self, String pattern) throws Exception {
		return match(self, pattern, -1);
	}

	public static String match(String self, String pattern, int matchGroup) throws Exception {
		Matcher matcher = compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS | MULTILINE).matcher(self);
		if (matcher.find()) {
			return firstCapturingGroup(matcher, matchGroup);
		} else {
			throw new Exception("Pattern not found: " + self);
		}
	}

	/**
	 * Return a list of all matching patterns or break.
	 */
	public static List<String> matchAll(String self, String pattern) throws Exception {
		return matchAll(self, pattern, -1);
	}

	public static List<String> matchAll(String self, String pattern, int matchGroup) throws Exception {
		List<String> matches = new ArrayList<String>();
		Matcher matcher = compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS | MULTILINE).matcher(self);
		while (matcher.find()) {
			matches.add(firstCapturingGroup(matcher, matchGroup));
		}

		if (matches.isEmpty()) {
			throw new Exception("Pattern not found: " + self);
		}
		return matches;
	}

	public static String firstCapturingGroup(Matcher self, int matchGroup) throws Exception {
		int g = matchGroup < 0 ? self.groupCount() > 0 ? 1 : 0 : matchGroup;

		// return the entire match
		if (g == 0) {
			return self.group();
		}

		// otherwise find first non-empty capturing group
		return IntStream.rangeClosed(g, self.groupCount()).mapToObj(self::group).filter(Objects::nonNull).map(String::trim).filter(s -> s.length() > 0).findFirst().orElseThrow(() -> {
			return new Exception(String.format("Capturing group %d not found", g));
		});
	}

	public static String replaceAll(String self, String pattern) {
		return self.replaceAll(pattern, "");
	}

	public static String removeAll(String self, String pattern) {
		return compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS | MULTILINE).matcher(self).replaceAll("").trim();
	}

	public static String removeIllegalCharacters(String self) {
		return FileUtilities.validateFileName(Normalization.normalizeQuotationMarks(self));
	}

	/**
	 * Replace space characters with a given characters.
	 *
	 * e.g. "Doctor Who" -> "Doctor_Who"
	 */
	public static String space(String self, String replacement) {
		return Normalization.normalizeSpace(self, replacement);
	}

	/**
	 * Replace colon to make the name more Windows friendly.
	 *
	 * e.g. "Sissi: The Young Empress" -> "Sissi - The Young Empress"
	 */
	public static String colon(String self, String colon) {
		return COLON.matcher(self).replaceAll(colon);
	}

	/**
	 * Replace colon to make the name more Windows friendly.
	 *
	 * e.g. "12:00 A.M.-1:00 A.M." -> "12.00 A.M.-1.00 A.M."
	 */
	public static String colon(String self, String ratio, String colon) {
		return COLON.matcher(RATIO.matcher(self).replaceAll(ratio)).replaceAll(colon);
	}

	/**
	 * Replace slash and backslash to make sure the result is not a file path.
	 *
	 * e.g. "V_MPEG4/ISO/AVC" -> "V_MPEG4.ISO.AVC"
	 */
	public static String slash(String self, String replacement) {
		return SLASH.matcher(self).replaceAll(replacement);
	}

	/**
	 * Upper-case all initials.
	 *
	 * e.g. "The Day a new Demon was born" -> "The Day A New Demon Was Born"
	 */
	public static String upperInitial(String self) {
		return replaceHeadTail(self, String::toUpperCase, String::toString);
	}

	/**
	 * Lower-case all letters that are not initials.
	 *
	 * e.g. "Gundam SEED" -> "Gundam Seed"
	 */
	public static String lowerTrail(String self) {
		return replaceHeadTail(self, String::toString, String::toLowerCase);
	}

	public static String replaceHeadTail(String self, Function<String, String> head, Function<String, String> tail) {
		Matcher matcher = compile("\\b(['`´]|\\p{Alnum})(\\p{Alnum}*)\\b", UNICODE_CHARACTER_CLASS).matcher(self);

		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(buffer, head.apply(matcher.group(1)) + tail.apply(matcher.group(2)));
		}

		return matcher.appendTail(buffer).toString();
	}

	public static String sortName(String self) {
		return sortName(self, "$2");
	}

	public static String sortName(String self, String replacement) {
		return compile("^(The|A|An)\\s(.+)", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self).replaceFirst(replacement).trim();
	}

	public static String sortInitial(String self) {
		// use primary initial, ignore The XY, A XY, etc
		char c = ascii(sortName(self)).charAt(0);

		if (Character.isDigit(c)) {
			return "0-9";
		} else if (Character.isLetter(c)) {
			return String.valueOf(c).toUpperCase();
		} else {
			return null;
		}
	}

	/**
	 * Get acronym, i.e. first letter of each word.
	 *
	 * e.g. "Deep Space 9" -> "DS9"
	 */
	public static String acronym(String self) {
		return compile("\\s|\\B\\p{Alnum}+", UNICODE_CHARACTER_CLASS).matcher(space(self, " ")).replaceAll("");
	}

	public static String truncate(String self, int limit) {
		if (limit >= self.length())
			return self;

		return self.substring(0, limit);
	}

	public static String truncate(String self, int hardLimit, String nonWordPattern) {
		if (hardLimit >= self.length())
			return self;

		int softLimit = 0;
		Matcher matcher = compile(nonWordPattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self);
		while (matcher.find()) {
			if (matcher.start() > hardLimit) {
				break;
			}
			softLimit = matcher.start();
		}
		return truncate(self, softLimit);
	}

	/**
	 * Return substring before the given pattern.
	 */
	public static String before(String self, String pattern) {
		Matcher matcher = compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self);

		// pattern was found, return leading substring, else return original value
		return matcher.find() ? self.substring(0, matcher.start()).trim() : self;
	}

	/**
	 * Return substring after the given pattern.
	 */
	public static String after(String self, String pattern) {
		Matcher matcher = compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self);

		// pattern was found, return trailing substring, else return original value
		return matcher.find() ? self.substring(matcher.end(), self.length()).trim() : self;
	}

	/**
	 * Find a matcher that matches the given pattern (case-insensitive)
	 */
	public static boolean findMatch(String self, String pattern) {
		if (pattern == null || pattern.isEmpty())
			return false;

		return compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self).find();
	}

	/**
	 * Find a matcher that matches the given pattern (case-insensitive) but matches only if the pattern is enclosed in word-boundaries
	 */
	public static boolean findWordMatch(String self, String pattern) {
		if (pattern == null || pattern.isEmpty())
			return false;

		return findMatch(self, "\\b(" + pattern + ")\\b");
	}

	/**
	 * Replace trailing parenthesis including any leading whitespace.
	 *
	 * e.g. "The IT Crowd (UK)" -> "The IT Crowd"
	 */
	public static String replaceTrailingBrackets(String self) {
		return replaceTrailingBrackets(self, "");
	}

	public static String replaceTrailingBrackets(String self, String replacement) {
		return compile("\\s*[(]([^)]*)[)]$", UNICODE_CHARACTER_CLASS).matcher(self).replaceAll(replacement);
	}

	/**
	 * Replace 'part identifier'.
	 *
	 * e.g. "Today Is the Day: Part 1" -> "Today Is the Day, Part 1" or "Today Is the Day (1)" -> "Today Is the Day, Part 1"
	 */
	public static String replacePart(String self) {
		return replacePart(self, "");
	}

	public static String replacePart(String self, String replacement) {
		// handle '(n)', '(Part n)' and ': Part n' like syntax
		String[] patterns = new String[] { "\\s*[(](\\w{1,3})[)]$", "\\W+Part (\\w+)\\W*$" };

		for (String pattern : patterns) {
			Matcher matcher = compile(pattern, CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS).matcher(self);
			if (matcher.find()) {
				return matcher.replaceAll(replacement).trim();
			}
		}

		// no pattern matches, nothing to replace
		return self;
	}

	/**
	 * Replace numbers 1..12 with Roman numerals
	 * 
	 * e.g. "Star Wars: Episode 4" -> "Star Wars: Episode IV"
	 */
	public static String roman(String self) {
		TreeMap<Integer, String> numerals = new TreeMap<Integer, String>();
		numerals.put(10, "X");
		numerals.put(9, "IX");
		numerals.put(5, "V");
		numerals.put(4, "IV");
		numerals.put(1, "I");

		StringBuffer s = new StringBuffer();
		Matcher m = compile("\\b\\d+\\b").matcher(self);
		while (m.find()) {
			int n = Integer.parseInt(m.group());
			m.appendReplacement(s, n >= 1 && n <= 12 ? roman(n, numerals) : m.group());
		}
		return m.appendTail(s).toString();
	}

	public static String roman(Integer n, TreeMap<Integer, String> numerals) {
		int l = numerals.floorKey(n);
		if (n == l) {
			return numerals.get(n);
		}
		return numerals.get(l) + roman(n - l, numerals);
	}

	/**
	 * Apply ICU transliteration
	 *
	 * @see http://userguide.icu-project.org/transforms/general
	 */
	public static String transliterate(String self, String transformIdentifier) {
		return Transliterator.getInstance(transformIdentifier).transform(self);
	}

	/**
	 * Convert Unicode to ASCII as best as possible. Works with most alphabets/scripts used in the world.
	 *
	 * e.g. "Österreich" -> "Osterreich" "カタカナ" -> "katakana"
	 */
	public static String ascii(String self) {
		return ascii(self, " ");
	}

	public static String ascii(String self, String fallback) {
		return Transliterator.getInstance("Any-Latin;Latin-ASCII;[:Diacritic:]remove").transform(asciiQuotes(self)).replaceAll("\\P{ASCII}+", fallback).trim();
	}

	public static String asciiQuotes(String self) {
		return Normalization.normalizeQuotationMarks(self);
	}

	public static boolean isLatin(String self) {
		return Normalizer.normalize(self, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}", "").matches("\\p{InBasicLatin}+");
	}

	/**
	 * Replace multiple replacement pairs
	 *
	 * e.g. replace(ä:'ae', ö:'oe', ü:'ue')
	 */
	public static String replace(String self, Map<?, ?> replace) {
		// the first two parameters are required, the rest of the parameter sequence is optional
		for (Entry<?, ?> it : replace.entrySet()) {
			if (it.getKey() instanceof Pattern) {
				self = ((Pattern) it.getKey()).matcher(self).replaceAll(it.getValue().toString());
			} else {
				self = self.replace(it.getKey().toString(), it.getValue().toString());
			}
		}
		return self;
	}

	public static String joining(Collection<?> self, String delimiter) throws Exception {
		String[] list = self.stream().filter(Objects::nonNull).map(Objects::toString).filter(s -> !s.isEmpty()).toArray(String[]::new);
		if (list.length > 0) {
			return String.join(delimiter, list);
		}

		throw new Exception("Collection did not yield any values: " + self);
	}

	public static String joiningDistinct(Collection<?> self, String delimiter, Closure<?>... mapper) throws Exception {
		Stream<?> stream = self.stream().filter(Objects::nonNull);

		// apply custom mappers if any
		if (mapper.length > 0) {
			stream = stream.flatMap(v -> stream(mapper).map(m -> m.call(v)).filter(Objects::nonNull));
		}

		// sort unique
		String[] list = stream.map(Objects::toString).filter(s -> !s.isEmpty()).distinct().sorted().toArray(String[]::new);
		if (list.length > 0) {
			return String.join(delimiter, list);
		}

		throw new Exception("Collection did not yield any values: " + self);
	}

	/**
	 * Unwind if an object does not satisfy the given predicate
	 *
	 * e.g. (0..9)*.check{it < 10}.sum()
	 */
	public static Object check(Object self, Closure<?> c) throws Exception {
		if (DefaultTypeTransformation.castToBoolean(c.call(self))) {
			return self;
		}

		throw new Exception("Object failed check: " + self);
	}

	/**
	 * File utilities
	 */
	public static File derive(File self, Object tag, Object... tagN) {
		// e.g. plex.derive{" by $director"}{" [$vc, $ac]"}
		String name = FileUtilities.getName(self);
		String extension = self.getName().substring(name.length());

		// e.g. Avatar (2009).eng.srt => Avatar (2009) 1080p.eng.srt
		if (SUBTITLE_FILES.accept(self)) {
			Matcher nameMatcher = releaseInfo.getSubtitleLanguageTagPattern().matcher(name);
			if (nameMatcher.find()) {
				extension = name.substring(nameMatcher.start() - 1) + extension;
				name = name.substring(0, nameMatcher.start() - 1);
			}
		}

		return new File(self.getParentFile(), concat(name, slash(concat(tag, null, tagN), ""), extension));
	}

	public static File getRoot(File self) {
		return FileUtilities.listPath(self).get(0);
	}

	public static File getTail(File self) {
		return FileUtilities.getRelativePathTail(self, FileUtilities.listPath(self).size() - 1);
	}

	public static List<File> listPath(File self) {
		return FileUtilities.listPath(self);
	}

	public static List<File> listPath(File self, int tailSize) {
		return FileUtilities.listPath(FileUtilities.getRelativePathTail(self, tailSize));
	}

	public static File getRelativePathTail(File self, int tailSize) {
		return FileUtilities.getRelativePathTail(self, tailSize);
	}

	public static long getDiskSpace(File self) {
		List<File> list = FileUtilities.listPath(self);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).exists()) {
				long usableSpace = list.get(i).getUsableSpace();
				if (usableSpace > 0) {
					return usableSpace;
				}
			}
		}
		return 0;
	}

	public static long getCreationDate(File self) throws IOException {
		BasicFileAttributes attr = Files.getFileAttributeView(self.toPath(), BasicFileAttributeView.class).readAttributes();
		long creationDate = attr.creationTime().toMillis();
		if (creationDate > 0) {
			return creationDate;
		}
		return attr.lastModifiedTime().toMillis();
	}

	public static LocalDateTime toDate(Long self) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(self), ZoneOffset.systemDefault());
	}

	public static File toFile(String self) {
		if (self == null || self.isEmpty()) {
			return null;
		}
		return new File(self);
	}

	public static File toFile(String self, String parent) {
		if (self == null || self.isEmpty()) {
			return null;
		}
		File file = new File(self);
		if (file.isAbsolute()) {
			return file;
		}
		return new File(parent, self);
	}

	public static Locale toLocale(String self) {
		return Locale.forLanguageTag(self);
	}

	public static String plus(String self, Closure<?> other) {
		return concat(self, other);
	}

	public static String plus(Closure<?> self, Object other) {
		return concat(self, other);
	}

	public static String plus(Language self, Object other) {
		return concat(self, other);
	}

	public static String plus(SimpleDate self, Object other) {
		return concat(self, other);
	}

	public static List<?> bounds(Iterable<?> self) {
		return Stream.of(DefaultGroovyMethods.min(self), DefaultGroovyMethods.max(self)).filter(Objects::nonNull).distinct().collect(toList());
	}

	public static String format(Temporal self, String pattern) {
		return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(self);
	}

	public static String format(TemporalAmount self, String pattern) {
		return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(LocalTime.MIDNIGHT.plus(self));
	}

	/**
	 * Episode utilities (EXPERIMENTAL)
	 */
	public static EpisodeInfo getInfo(Episode self) throws Exception {
		if (TheTVDB.getIdentifier().equals(self.getSeriesInfo().getDatabase())) {
			return TheTVDB.getEpisodeInfo(self.getId(), Locale.ENGLISH);
		}
		return null;
	}

	public static List<String> getActors(SeriesInfo self) throws Exception {
		if (TheTVDB.getIdentifier().equals(self.getDatabase())) {
			return TheTVDB.getActors(self.getId(), Locale.ENGLISH).stream().map(Person::getName).collect(toList());
		}
		return null;
	}

	public static Map<String, List<String>> getAlternativeTitles(Movie self) throws Exception {
		if (self.getTmdbId() > 0) {
			return TheMovieDB.getAlternativeTitles(self.getTmdbId());
		}
		return null;
	}

	private ExpressionFormatMethods() {
		throw new UnsupportedOperationException();
	}

}
