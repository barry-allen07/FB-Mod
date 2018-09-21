
package net.filebot.similarity;


import static net.filebot.util.FileUtilities.*;

import java.io.File;


public class FileNameMetric implements SimilarityMetric {

	@Override
	public float getSimilarity(Object o1, Object o2) {
		String s1 = getFileName(o1);
		if (s1 == null || s1.isEmpty())
			return 0;

		String s2 = getFileName(o2);
		if (s2 == null || s2.isEmpty())
			return 0;

		return s1.startsWith(s2) || s2.startsWith(s1) ? 1 : 0;
	}


	protected String getFileName(Object object) {
		if (object instanceof File) {
			// name without extension normalized to lower-case
			return getName((File) object).trim().toLowerCase();
		}

		return null;
	}

}
