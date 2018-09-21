package net.filebot.similarity;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;

import net.filebot.media.SmartSeasonEpisodeMatcher;
import net.filebot.similarity.SeriesNameMatcher.SeriesNameCollection;

public class SeriesNameMatcherTest {

	SeriesNameMatcher matcher = new SeriesNameMatcher(new SmartSeasonEpisodeMatcher(SeasonEpisodeMatcher.DEFAULT_SANITY, true), new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.ENGLISH));

	@Test
	public void whitelist() {
		// ignore recurring word sequences when matching episode patterns
		String[] names = new String[] { "Test 101 - 01", "Test 101 - 02" };

		assertArrayEquals(new String[] { "Test 101" }, matcher.matchAll(names).toArray());
	}

	@Test
	public void threshold() {
		// ignore recurring word sequences when matching episode patterns
		String[] names = new String[] { "Test 1 of 101", "Test 2 of 101", "Test 3 of 101" };

		assertArrayEquals(new String[] { "Test" }, matcher.matchAll(names).toArray());
	}

	@Test
	public void matchBeforeSeasonEpisodePattern() {
		assertEquals("The Test -", matcher.matchByEpisodeIdentifier("The Test - 1x01"));
		assertEquals("Mushishi_-_", matcher.matchByEpisodeIdentifier("Mushishi_-_1x01_-_The_Green_Gathering"));
	}

	@Test
	public void normalize() {
		// non-letter and non-digit characters
		assertEquals("The Test", matcher.normalize("_The_Test_-_ ..."));

		// brackets
		assertEquals("Luffy", matcher.normalize("[strawhat] Luffy (D.) [#Monkey]"));

		// invalid brackets
		assertEquals("strawhat Luffy", matcher.normalize("(strawhat [Luffy (#Monkey)"));
	}

	@Test
	public void firstCommonSequence() {
		String[] seq1 = "Common Name 1 Any Title".split(" ");
		String[] seq2 = "abc xyz Common Name 2 Any Title".split(" ");

		// check if common sequence can be determined
		assertArrayEquals(new String[] { "Common", "Name" }, matcher.firstCommonSequence(seq1, seq2, 2, String.CASE_INSENSITIVE_ORDER));

		// check if max start index is working
		assertArrayEquals(null, matcher.firstCommonSequence(seq1, seq2, 0, String.CASE_INSENSITIVE_ORDER));
		assertArrayEquals(null, matcher.firstCommonSequence(seq2, seq1, 1, String.CASE_INSENSITIVE_ORDER));
	}

	@Test
	public void firstCharacterCaseBalance() {
		SeriesNameCollection n = new SeriesNameCollection();

		assertTrue(n.firstCharacterCaseBalance("My Name is Earl") > n.firstCharacterCaseBalance("My Name Is Earl"));
		assertTrue(n.firstCharacterCaseBalance("My Name is Earl") > n.firstCharacterCaseBalance("my name is earl"));

		// boost upper case ration
		assertTrue(n.firstCharacterCaseBalance("Roswell") > n.firstCharacterCaseBalance("roswell"));

	}
}
