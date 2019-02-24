package net.filebot.similarity;

public class StringEqualsMetric implements SimilarityMetric {

	@Override
	public float getSimilarity(Object o1, Object o2) {
		if (o1 == null || o2 == null)
			return 0;

		String s1 = normalize(o1);
		String s2 = normalize(o2);

		if (s1.isEmpty() || s2.isEmpty())
			return 0;

		return s1.equals(s2) ? 1 : 0;
	}

	protected String normalize(Object object) {
		return object.toString().trim().toLowerCase();
	}

}
