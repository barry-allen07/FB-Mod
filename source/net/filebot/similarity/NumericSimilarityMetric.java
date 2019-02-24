package net.filebot.similarity;

import static java.util.stream.Collectors.*;
import static net.filebot.util.StringUtilities.*;
import static org.simmetrics.builders.StringMetricBuilder.*;

import java.util.List;

import org.simmetrics.StringMetric;
import org.simmetrics.metrics.BlockDistance;
import org.simmetrics.tokenizers.AbstractTokenizer;

public class NumericSimilarityMetric implements SimilarityMetric {

	private final StringMetric metric = with(new BlockDistance<String>()).tokenize(new NumberTokeniser()).build();

	@Override
	public float getSimilarity(Object o1, Object o2) {
		return metric.compare(normalize(o1), normalize(o2));
	}

	protected String normalize(Object object) {
		// no need to do anything special here, because we don't care about anything but number patterns anyway
		return object.toString();
	}

	private static class NumberTokeniser extends AbstractTokenizer {

		@Override
		public List<String> tokenizeToList(String input) {
			return matchIntegers(input).stream().map(String::valueOf).collect(toList());
		}
	}

}
