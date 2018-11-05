package net.filebot.similarity;

import java.io.File;

import net.filebot.web.SimpleDate;

public class DateMetric implements SimilarityMetric {

	private final DateMatcher matcher;

	public DateMetric(DateMatcher matcher) {
		this.matcher = matcher;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		SimpleDate d1 = parse(o1);
		if (d1 == null)
			return 0;

		SimpleDate d2 = parse(o2);
		if (d2 == null)
			return 0;

		return d1.equals(d2) ? 1 : -1;
	}

	public SimpleDate parse(Object object) {
		if (object instanceof File) {
			return matcher.match((File) object);
		}
		return matcher.match(object.toString());
	}

}
