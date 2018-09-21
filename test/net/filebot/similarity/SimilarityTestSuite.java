package net.filebot.similarity;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ SeriesNameMatcherTest.class, SeasonEpisodeMatcherTest.class, DateMatcherTest.class, NameSimilarityMetricTest.class, NumericSimilarityMetricTest.class, SeasonEpisodeMetricTest.class, SimilarityComparatorTest.class })
public class SimilarityTestSuite {

}
