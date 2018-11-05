
package net.filebot.similarity;


public class MetricMin implements SimilarityMetric {

	private final SimilarityMetric metric;
	private final float minValue;


	public MetricMin(SimilarityMetric metric, float minValue) {
		this.metric = metric;
		this.minValue = minValue;
	}


	@Override
	public float getSimilarity(Object o1, Object o2) {
		return Math.max(metric.getSimilarity(o1, o2), minValue);
	}

}
