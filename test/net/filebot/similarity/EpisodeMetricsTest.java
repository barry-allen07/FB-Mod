package net.filebot.similarity;

import static net.filebot.similarity.EpisodeMetrics.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.filebot.web.Episode;
import net.filebot.web.SimpleDate;

public class EpisodeMetricsTest {

	@Test
	public void substringMetrics() {
		Episode eY1T1 = new Episode("Doctor Who", 1, 1, "Rose");

		File fY1T1 = new File("Doctor Who (2005)/Doctor Who - 1x01 - Rose");
		File fY2T2 = new File("Doctor Who (1963)/Doctor Who - 1x01 - An Unearthly Child");

		assertEquals(0.5, SubstringFields.getSimilarity(eY1T1, fY1T1), 0.1);
		assertEquals(0.5, SubstringFields.getSimilarity(eY1T1, fY2T2), 0.1);
	}

	@Test
	public void matcherLevel2() throws Exception {
		List<File> files = new ArrayList<File>();
		List<Episode> episodes = new ArrayList<Episode>();

		files.add(new File("Greek/Greek - S01E19 - No Campus for Old Rules"));
		files.add(new File("Veronica Mars - Season 1/Veronica Mars [1x19] Hot Dogs"));
		episodes.add(new Episode("Veronica Mars", 1, 19, "Hot Dogs"));
		episodes.add(new Episode("Greek", 1, 19, "No Campus for Old Rules"));

		SimilarityMetric[] metrics = new SimilarityMetric[] { EpisodeIdentifier, SubstringFields };
		List<Match<File, Episode>> m = new Matcher<File, Episode>(files, episodes, false, metrics).match();

		assertEquals("Greek - S01E19 - No Campus for Old Rules", m.get(0).getValue().getName());
		assertEquals("Greek - 1x19 - No Campus for Old Rules", m.get(0).getCandidate().toString());
		assertEquals("Veronica Mars [1x19] Hot Dogs", m.get(1).getValue().getName());
		assertEquals("Veronica Mars - 1x19 - Hot Dogs", m.get(1).getCandidate().toString());
	}

	@Test
	public void nameIgnoreEmbeddedChecksum() {
		assertEquals(1, Name.getSimilarity("test", "test [EF62DF13]"), 0);
	}

	@Test
	public void numericIgnoreEmbeddedChecksum() {
		assertEquals(1, Numeric.getSimilarity("S01E02", "Season 1, Episode 2 [00A01E02]"), 0);
	}

	@Test
	public void numericNumbers() {
		String fn = "SEED - 01 - [X 2.0]";
		Episode e1 = new Episode("SEED", null, 1, "Enraged Eyes", 1, null, new SimpleDate(2004, 10, 9), null, null);
		Episode s1 = new Episode("SEED", null, null, "EDITED", null, 1, new SimpleDate(2005, 1, 29), null, null);

		assertEquals(0.5, Numeric.getSimilarity(fn, e1), 0.01);
		assertEquals(0.5, Numeric.getSimilarity(fn, s1), 0.01);
	}

}
