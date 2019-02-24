
package net.filebot.similarity;


import static org.junit.Assert.*;

import org.junit.Test;


public class NameSimilarityMetricTest {

	private static NameSimilarityMetric metric = new NameSimilarityMetric();


	@Test
	public void getSimilarity() {
		// normalize separators, lower-case
		assertEquals(1, metric.getSimilarity("test s01e01 first", "test.S01E01.First"), 0);
		assertEquals(1, metric.getSimilarity("test s01e02 second", "test_[S01E02]_Second"), 0);
		assertEquals(1, metric.getSimilarity("test s01e03 third", "__test__S01E03__Third__"), 0);
		assertEquals(1, metric.getSimilarity("test s01e04 four", "test   s01e04     four"), 0);
	}

}
