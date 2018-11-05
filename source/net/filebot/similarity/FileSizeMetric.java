
package net.filebot.similarity;


import java.io.File;


public class FileSizeMetric implements SimilarityMetric {

	@Override
	public float getSimilarity(Object o1, Object o2) {
		long l1 = getLength(o1);
		if (l1 < 0)
			return 0;

		long l2 = getLength(o2);
		if (l2 < 0)
			return 0;

		// objects have the same non-negative length
		return l1 == l2 ? 1 : -1;
	}


	protected long getLength(Object object) {
		if (object instanceof File) {
			return ((File) object).length();
		}

		return -1;
	}

}
