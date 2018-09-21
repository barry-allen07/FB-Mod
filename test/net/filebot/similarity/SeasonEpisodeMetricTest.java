
package net.filebot.similarity;


import static org.junit.Assert.*;

import org.junit.Test;


public class SeasonEpisodeMetricTest {

	private static SeasonEpisodeMetric metric = new SeasonEpisodeMetric();


	@Test
	public void getSimilarity() {
		// single pattern match, single episode match
		assertEquals(1.0, metric.getSimilarity("1x01", "s01e01"), 0);

		// multiple pattern matches, single episode match
		assertEquals(1.0, metric.getSimilarity("1x02a", "101 102 103"), 0);

		// multiple pattern matches, only partial match (season)
		assertEquals(0.5, metric.getSimilarity("1x03b", "104 105 106"), 0);

		// no pattern match, no episode match
		assertEquals(0.0, metric.getSimilarity("abc", "xyz"), 0);
	}

}
