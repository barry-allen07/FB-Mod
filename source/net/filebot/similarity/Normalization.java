package net.filebot.similarity;

import static java.util.regex.Pattern.*;
import static net.filebot.util.RegularExpressions.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Normalization {

	public static final Pattern APOSTROPHE = compile("['`´‘’ʻ]+");
	public static final Pattern PUNCTUATION_OR_SPACE = compile("[\\p{Punct}\\p{Space}]+", UNICODE_CHARACTER_CLASS);
	public static final Pattern WORD_SEPARATOR_PUNCTUATION = compile("[:?._]");

	public static final Pattern TRAILING_PARENTHESIS = compile("(?<!^)[(]([^)]*)[)]$");
	public static final Pattern TRAILING_PUNCTUATION = compile("[!?.]+$");
	public static final Pattern EMBEDDED_CHECKSUM = compile("[\\(\\[](\\p{XDigit}{8})[\\]\\)]");

	private static final Pattern[] BRACKETS = new Pattern[] { compile("\\([^\\(]*\\)"), compile("\\[[^\\[]*\\]"), compile("\\{[^\\{]*\\}") };

	// ' and " all characters that are more or less equivalent
	private static final char[][] QUOTES = { { '\'', '\u0060', '\u00b4', '\u2018', '\u2019', '\u02bb' }, { '\"', '\u201c', '\u201d' } };

	public static String normalizeQuotationMarks(String name) {
		for (char[] cs : QUOTES) {
			for (char c : cs) {
				name = name.replace(c, cs[0]);
			}
		}
		return name;
	}

	public static String trimTrailingPunctuation(CharSequence name) {
		return normalize(name, TRAILING_PUNCTUATION, "");
	}

	public static String normalizePunctuation(String name) {
		return normalizePunctuation(name, "", " ");
	}

	public static String normalizePunctuation(String name, String apostrophe, String space) {
		// remove/normalize special characters
		Pattern[] pattern = { APOSTROPHE, PUNCTUATION_OR_SPACE };
		String[] replacement = { apostrophe, space };

		return normalize(name, pattern, replacement);
	}

	public static String normalizeBrackets(String name) {
		// remove group names and checksums, any [...] or (...)
		return normalize(name, BRACKETS, " ");
	}

	public static String normalizeSpace(String name, String space) {
		Pattern[] patterns = { WORD_SEPARATOR_PUNCTUATION, SPACE };
		String[] replacements = { " ", space };

		return normalize(name, patterns, replacements);
	}

	public static String replaceSpace(CharSequence name, String replacement) {
		return normalize(name, SPACE, replacement);
	}

	public static String replaceColon(String name, String ratio, String colon) {
		Pattern[] pattern = { RATIO, COLON };
		String[] replacement = { ratio, colon };

		return normalize(name, pattern, replacement);
	}

	public static String getEmbeddedChecksum(CharSequence name) {
		Matcher m = EMBEDDED_CHECKSUM.matcher(name);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	public static String removeEmbeddedChecksum(CharSequence name) {
		// match embedded checksum and surrounding brackets
		return normalize(name, EMBEDDED_CHECKSUM, "");
	}

	public static String removeTrailingBrackets(CharSequence name) {
		// remove trailing braces, e.g. Doctor Who (2005) -> Doctor Who
		return normalize(name, TRAILING_PARENTHESIS, "");
	}

	private static String normalize(CharSequence name, Pattern pattern, String replacement) {
		return pattern.matcher(name).replaceAll(Matcher.quoteReplacement(replacement)).trim();
	}

	private static String normalize(String name, Pattern[] pattern, String replacement) {
		for (int i = 0; i < pattern.length; i++) {
			name = normalize(name, pattern[i], replacement);
		}
		return name;
	}

	private static String normalize(String name, Pattern[] pattern, String[] replacement) {
		for (int i = 0; i < pattern.length; i++) {
			name = normalize(name, pattern[i], replacement[i]);
		}
		return name;
	}

	public static String truncateText(String title, int limit) {
		if (title == null || title.length() < limit) {
			return title;
		}

		String[] words = SPACE.split(title);
		StringBuilder s = new StringBuilder();

		for (int i = 0; i < words.length && s.length() + words[i].length() < limit; i++) {
			if (i > 0) {
				s.append(' ');
			}
			s.append(words[i]);
		}

		return s.toString().trim();
	}

}
