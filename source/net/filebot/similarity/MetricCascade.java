package net.filebot.similarity;

import static java.util.Arrays.*;

public class MetricCascade implements SimilarityMetric {

	private final SimilarityMetric[] cascade;

	public MetricCascade(SimilarityMetric... cascade) {
		this.cascade = cascade;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		float f = 0;
		for (SimilarityMetric metric : cascade) {
			float similarity = metric.getSimilarity(o1, o2);
			if (Math.abs(similarity) >= Math.abs(f)) {
				// perfect match, ignore remaining metrics
				if (similarity >= 1) {
					return similarity;
				}

				// possible match or perfect negative match
				f = similarity;
			}
		}
		return f;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ' ' + asList(cascade);
	}

}
