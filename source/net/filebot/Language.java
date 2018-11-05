package net.filebot;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Stream;

public class Language implements Serializable {

	// ISO 639-1 code
	private final String iso_639_1;

	// ISO 639-3 code (mostly identical to ISO 639-2/T)
	private final String iso_639_3;

	// ISO 639-2/B code
	private final String iso_639_2B;

	// BCP 47 language tag
	private final String tag;

	// Language name
	private final String[] names;

	public Language(String iso_639_1, String iso_639_3, String iso_639_2B, String tag, String[] names) {
		this.iso_639_1 = iso_639_1;
		this.iso_639_3 = iso_639_3;
		this.iso_639_2B = iso_639_2B;
		this.tag = tag;
		this.names = names.clone();
	}

	public String getCode() {
		return iso_639_1;
	}

	public String getISO2() {
		return iso_639_1;
	}

	public String getISO3() {
		return iso_639_3; // 3-letter code
	}

	public String getISO3B() {
		return iso_639_2B; // alternative 3-letter code
	}

	public String getTag() {
		return tag;
	}

	public String getName() {
		return names[0];
	}

	public List<String> getNames() {
		return unmodifiableList(asList(names));
	}

	@Override
	public String toString() {
		return iso_639_3;
	}

	public Locale getLocale() {
		Locale locale = Locale.forLanguageTag(tag);

		// e.g. x-jat
		if (locale == null || locale.getLanguage().isEmpty()) {
			return new Locale(iso_639_1);
		}

		return locale;
	}

	public boolean matches(String code) {
		return Stream.concat(Stream.of(iso_639_1, iso_639_2B, iso_639_3, tag), Stream.of(names)).anyMatch(c -> c.equalsIgnoreCase(code));
	}

	@Override
	public Language clone() {
		return new Language(iso_639_1, iso_639_3, iso_639_2B, tag, names);
	}

	public static final Comparator<Language> ALPHABETIC_ORDER = comparing(Language::getName, String::compareToIgnoreCase);

	public static Language getLanguage(String code) {
		if (code == null || code.isEmpty()) {
			return null;
		}

		try {
			String[] values = TAB.split(getProperty(code), 4);
			return new Language(code, values[0], values[1], values[2], TAB.split(values[3]));
		} catch (Exception e) {
			debug.finest(cause(e)); // log and ignore
		}

		return null;
	}

	public static List<Language> getLanguages(String... codes) {
		return stream(codes).map(Language::getLanguage).collect(toList());
	}

	public static Language getLanguage(Locale locale) {
		return locale == null ? null : findLanguage(locale.getLanguage());
	}

	public static Language findLanguage(String language) {
		return availableLanguages().stream().filter(it -> it.matches(language)).findFirst().orElse(null);
	}

	public static String getStandardLanguageCode(String lang) {
		try {
			return Language.findLanguage(lang).getISO3();
		} catch (Exception e) {
			return null;
		}
	}

	public static List<Language> availableLanguages() {
		String languages = getProperty("languages.ui");
		return getLanguages(SPACE.split(languages));
	}

	public static List<Language> commonLanguages() {
		String languages = getProperty("languages.common");
		return getLanguages(SPACE.split(languages));
	}

	public static List<Language> preferredLanguages() {
		// English | system language | common languages
		Stream<String> codes = Stream.of(Locale.ENGLISH, Locale.getDefault()).map(Locale::getLanguage);

		// append common languages
		codes = Stream.concat(codes, SPACE.splitAsStream(getProperty("languages.common"))).distinct();

		return codes.map(Language::getLanguage).collect(toList());
	}

	private static String getProperty(String key) {
		try {
			return ResourceBundle.getBundle(Language.class.getName()).getString(key);
		} catch (MissingResourceException e) {
			throw new IllegalArgumentException("Illegal language code: " + key);
		}
	}

}
