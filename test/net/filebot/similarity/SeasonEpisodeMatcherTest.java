package net.filebot.similarity;

import static java.util.Arrays.*;
import static net.filebot.similarity.SeasonEpisodeMatcher.SxE.*;
import static org.junit.Assert.*;

import org.junit.Test;

import net.filebot.media.MediaDetection;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;

public class SeasonEpisodeMatcherTest {

	private static SeasonEpisodeMatcher matcher = new SeasonEpisodeMatcher(SeasonEpisodeMatcher.DEFAULT_SANITY, false);

	@Test
	public void patternPrecedence() {
		// S01E01 pattern has highest precedence
		assertEquals(new SxE(1, 3), matcher.match("Test.101.1x02.S01E03").get(0));

		assertEquals(new SxE(1, 2), matcher.match("[s01]_[e02]").get(0));
		assertEquals(asList(new SxE(2013, 10), new SxE(20, 13)), matcher.match("2013.P10"));
		assertEquals(asList(new SxE(2013, 10), new SxE(20, 13)), matcher.match("2013.P10"));

		assertEquals(new SxE(null, 11), matcher.match("wsop.2013.me.p11.720p-yestv").get(0));
		assertEquals(new SxE(null, 18), matcher.match("World.Series.Of.Poker.2013.Main.Event.Part18.480p.HDTV.x264-mSD").get(0));

		assertEquals(asList(new SxE(null, 01), new SxE(1, 01), new SxE(null, 101)), matcher.match("alias.101.Part1"));
		assertEquals(asList(new SxE(null, 01)), matcher.match("Documentaries.1of6"));

		// multi-episode numbers
		assertEquals("[1x01, 1x02]", matcher.match("Test.42.s01e01.s01e02.300").toString());
		assertEquals("[1x01, 1x02]", matcher.match("1x01-02").toString());
		assertEquals("[3x11, 3x12, 3x13, 3x14]", matcher.match("03x11-03x12-03x13-03x14").toString());
		assertEquals("[9x09, 9x10]", matcher.match("09x09-09x10").toString());
		assertEquals("[2x17, 2x18]", matcher.match("[Season 2 Episode 17-18]").toString());
	}

	@Test
	public void pattern_1x01() {
		assertEquals(new SxE(1, 1), matcher.match("1x01").get(0));

		// test multiple matches
		assertEquals(new SxE(1, 2), matcher.match("Test - 1x01 and 1x02 - Multiple MatchCollection").get(1));

		// test high values
		assertEquals(null, matcher.match("Test - 12x345 - High Values"));

		// test look-ahead and look-behind
		assertEquals(new SxE(1, 3), matcher.match("Test_-_103_[1280x720]").get(0));

		// test look-ahead and look-behind
		assertEquals(new SxE(1, 4), new SeasonEpisodeMatcher(null, true).match("Atlantis.2013.1x04.Twist.of.Fate").get(0));
	}

	@Test
	public void pattern_S01E01() {
		assertEquals(new SxE(1, 1), matcher.match("S01E01").get(0));
		assertEquals(new SxE(2010, 0), matcher.match("S2010E00").get(0));

		// test multiple matches
		assertEquals(new SxE(1, 2), matcher.match("S01E01 and S01E02 - Multiple MatchCollection").get(1));

		// test separated values
		assertEquals(new SxE(1, 3), matcher.match("[s01]_[e03]").get(0));

		// test high values
		assertEquals(new SxE(12, 345), matcher.match("Test - S12E345 - High Values").get(0));

		// test odd patterns
		assertEquals(new SxE(3, 4), matcher.match("S3-04").get(0));
	}

	@Test
	public void pattern_101() {
		assertEquals(new SxE(1, 1), matcher.match("Test.101").get(0));

		// test 2-digit number
		assertEquals(new SxE(UNDEFINED, 2), matcher.match("02").get(0));

		// test high values
		assertEquals(new SxE(10, 1), matcher.match("[Test]_1001_High_Values").get(0));

		// test season digits <= 19
		assertEquals(new SxE(null, 16), matcher.match("E16").get(0));

		// test ambiguous match processing
		assertEquals(asList(new SxE(1, 1), new SxE(UNDEFINED, 101)), matcher.match("Test.101"));

		// test 4-digit
		assertEquals(asList(new SxE(23, 21)), matcher.match("the.simpsons.2321.hdtv-lol"));
	}

	@Test
	public void multiEpisodePatterns() {
		assertEquals(new SxE(1, 1), matcher.match("s01e01-02-03-04").get(0));
		assertEquals(new SxE(1, 4), matcher.match("s01e01-02-03-04").get(3));

		assertEquals(new SxE(1, 1), matcher.match("s01e01e02e03e04").get(0));
		assertEquals(new SxE(1, 4), matcher.match("s01e01e02e03e04").get(3));

		assertEquals(new SxE(1, 1), matcher.match("1x01-02-03-04").get(0));
		assertEquals(new SxE(1, 4), matcher.match("1x01-02-03-04").get(3));

		assertEquals(new SxE(1, 1), matcher.match("1x01x02x03x04").get(0));
		assertEquals(new SxE(1, 4), matcher.match("1x01x02x03x04").get(3));

		assertEquals(new SxE(1, 1), matcher.match("[s01]_[e01-02-03-04]").get(0));
		assertEquals(new SxE(1, 4), matcher.match("[s01]_[e01-02-03-04]").get(3));

		assertEquals(new SxE(1, 1), matcher.match("1x01.1x02.1x03.1x04").get(0));
		assertEquals(new SxE(1, 4), matcher.match("1x01.1x02.1x03.1x04").get(3));

		assertEquals(new SxE(null, 4), matcher.match("E1E2E3E4").get(3));

		assertEquals("[05]", matcher.match("05.Chapter.04+1").toString());

		assertEquals("[285, 286]", matcher.match("Episode_285-286").toString());
		assertEquals("[290, 291, 292, 293, 294, 295]", matcher.match("Episode_290-295").toString());
	}

	@Test
	public void multiEpisodePatternsFalsePositive() {
		assertEquals("[1x01, 01, 12]", matcher.match("Complete Season 01 (EP 01-12)/01").toString());
		assertEquals("[1x01, 01, 12]", matcher.match("Complete Season 01 (EP 01-12)/12").toString());
	}

	@Test
	public void withReleaseInfo() {
		assertEquals("[7x20]", matcher.match("720p").toString());

		SeasonEpisodeMatcher smartMatcher = MediaDetection.getSeasonEpisodeMatcher(true);
		assertEquals(null, smartMatcher.match("720p"));
	}

}
