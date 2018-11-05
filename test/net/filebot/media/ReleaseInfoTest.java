
package net.filebot.media;

import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class ReleaseInfoTest {

	ReleaseInfo info = new ReleaseInfo();

	@Test
	public void getVideoSource() {
		assertEquals("DVDRip", info.getVideoSource("Jurassic.Park[1993]DvDrip-aXXo"));
	}

	@Test
	public void getReleaseGroup() throws Exception {
		assertEquals("aXXo", info.getReleaseGroup("Jurassic.Park[1993]DvDrip-aXXo"));
		assertEquals("aXXo", info.getReleaseGroup("Jurassic.Park[1993]DvDrip-[aXXo]"));
		assertEquals("aXXo[RARBG]", info.getReleaseGroup("Jurassic.Park[1993]DvDrip-aXXo[RARBG]"));
	}

	@Test
	public void getReleaseGroupFalseNegative() throws Exception {
		assertEquals(null, info.getReleaseGroup("The.aXXo.Movie.2005"));
		assertEquals(null, info.getReleaseGroup("The aXXo Movie"));

	}

	@Test
	public void getReleaseGroupPattern() throws Exception {
		assertEquals("[]_Infinite_Stratos_2_-_01_[]", clean(info.getReleaseGroupTrimPattern(), "[HorribleSubs]_Infinite_Stratos_2_-_01_[HorribleSubs]"));
		assertEquals("[]_Infinite_Stratos_2_-_01_[]", clean(info.getReleaseGroupPattern(true), "[HorribleSubs]_Infinite_Stratos_2_-_01_[HorribleSubs]"));
		assertEquals("_Infinite_Stratos_2_-_01_", clean(info.getReleaseGroupPattern(false), "HorribleSubs_Infinite_Stratos_2_-_01_HorribleSubs"));

		assertEquals("DVL", info.getReleaseGroup("Movie-DVL"));
		assertEquals("iMBT", info.getReleaseGroup("The.Legend.Of.Zorro-iMBT"));

		assertEquals("[The Legend of the Blue Sea]", info.cleanRelease(singleton("The.Legend.of.the.Blue.Sea.E01"), false).toString());
		assertEquals("[The Legend of the Blue Sea]", info.cleanRelease(singleton("[Legend].The.Legend.of.the.Blue.Sea.E01-Legend"), false).toString());
	}

	@Test
	public void getClutterBracketPattern() throws Exception {
		assertEquals("John [2016]  (ENG)", clean(info.getClutterBracketPattern(true), "John [2016] [Action, Drama] (ENG)"));
		assertEquals("John [2016]  ", clean(info.getClutterBracketPattern(false), "John [2016] [Action, Drama] (ENG)"));
	}

	private static String clean(Pattern p, String s) {
		return p.matcher(s).replaceAll("");
	}

}
