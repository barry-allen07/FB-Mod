package net.filebot.similarity;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SimilarityComparatorTest {

	private static List<String> generateWords() {
		return asList("Hello", "Hallo", "12345", "Holla", "Hey", "0123456789", "Hello World", "Hello Test");
	}

	private static Map<String, String> generateTranslations() {
		Map<String, String> m = new HashMap<>();
		m.put("Hello", "Hello");
		m.put("Hallo", "Hello");
		m.put("Holla", "Hello");
		m.put("Hey", "Hello");
		return m;
	}

	@Test
	public void defaultUsage() {
		SimilarityComparator<String, String> c = SimilarityComparator.compareTo("Hello", String::toString);

		List<String> phrases = generateWords();
		phrases.sort(c);

		assertEquals("[Hello, Hello Test, Hello World, Hallo, 12345, Holla, Hey, 0123456789]", phrases.toString());
	}

	@Test
	public void accumulateMapper() {
		Map<String, String> dict = generateTranslations();
		SimilarityComparator<String, String> c = new SimilarityComparator<String, String>(new NameSimilarityMetric(), singleton("Hello"), (it) -> asList(it, dict.get(it)));

		List<String> phrases = generateWords();
		phrases.sort(c);

		assertEquals("[Hello, Hallo, Holla, Hey, Hello Test, Hello World, 12345, 0123456789]", phrases.toString());
	}

}
