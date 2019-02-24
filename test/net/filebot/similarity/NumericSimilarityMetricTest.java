
package net.filebot.similarity;


import static org.junit.Assert.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import net.filebot.util.TestUtil;


@RunWith(Parameterized.class)
public class NumericSimilarityMetricTest {

	private static NumericSimilarityMetric metric = new NumericSimilarityMetric();

	private static Map<String, String> matches = createMatches();


	public static Map<String, String> createMatches() {
		Map<String, String> matches = new LinkedHashMap<String, String>();

		// lots of naming variations
		matches.put("Test - 1x01", "test.S01E01.Pilot.HDTV.XviD-FQM");
		matches.put("Test - 1x02", "test.S01E02.HDTV.XviD-DIMENSION");
		matches.put("Test - 1x03", "test.S01E03.Third.time.is.the.charm.DSR.XviD-2SD");
		matches.put("Test - 1x04", "test.S01E04.Four.Square.HDTV-FQM.eng");
		matches.put("Test - 1x05", "test.season.1.episode.05.DSR.eng");
		matches.put("Test - 1x06", "test.1x06.dsr.V1");
		matches.put("Test - 1x07", "test.s01e07.dsr.tempt.12.V0");
		matches.put("Test - 1x08", "test.s01e08.dsr.tempt.16.V0");
		matches.put("Test - 1x09", "Test - 1x09");
		matches.put("Test - 1x10", "test.s01e10.dsr.xvid-2sd.VO");
		matches.put("Test - 1x11", "Test - 01x11 - The Question");
		matches.put("Test - 1x12", "test.1x12.iht.VO");
		matches.put("Test - 1x13", "Test.S01E13.DSR.XviD-0TV");
		matches.put("Test - 1x14", "Test.S01E14.DSR.XviD-2SD");
		matches.put("Test - 1x15", "Test.S01E15.DSR.XviD-0TV");
		matches.put("Test - 1x16", "test.1x16.0tv.VO");
		matches.put("Test - 1x17", "Test.S01E17.42.is.the.answer.DSR.XviD-0TV");
		matches.put("Test - 1x18", "Test.S01E18.DSR.XviD-0TV");

		// lots of numbers
		matches.put("The 4400 - 1x01", "the.4400.s1e01.pilot.720p");
		matches.put("The 4400 - 4x04", "the.4400.s4e04.eden.720p");

		return matches;
	}


	@Parameters
	public static Collection<Object[]> createParameters() {
		return TestUtil.asParameters(matches.keySet());
	}

	private final String normalizedName;


	public NumericSimilarityMetricTest(String normalizedName) {
		this.normalizedName = normalizedName;
	}


	@Test
	public void getBestMatch() {
		String match = getBestMatch(normalizedName, matches.values());

		assertEquals(matches.get(normalizedName), match);
	}


	public String getBestMatch(String value, Collection<String> testdata) {
		double maxSimilarity = -1;
		String mostSimilar = null;

		for (String current : testdata) {
			double similarity = metric.getSimilarity(value, current);

			if (similarity > maxSimilarity) {
				maxSimilarity = similarity;
				mostSimilar = current;
			}
		}

		return mostSimilar;
	}
}
