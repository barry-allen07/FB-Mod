package net.filebot.similarity;

import static net.filebot.similarity.CommonSequenceMatcher.*;
import static net.filebot.similarity.Normalization.*;

import java.util.Locale;

public class SequenceMatchSimilarity implements SimilarityMetric {

	private final CommonSequenceMatcher commonSequenceMatcher;

	public SequenceMatchSimilarity() {
		this(10, false);
	}

	public SequenceMatchSimilarity(int commonSequenceMaxStartIndex, boolean returnFirstMatch) {
		this.commonSequenceMatcher = new CommonSequenceMatcher(getLenientCollator(Locale.ENGLISH), commonSequenceMaxStartIndex, returnFirstMatch);
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		String s1 = normalize(o1);
		String s2 = normalize(o2);

		// match common word sequence
		String match = match(s1, s2);
		if (match == null || match.isEmpty())
			return 0;

		return similarity(match, s1, s2);
	}

	protected float similarity(String match, String s1, String s2) {
		return (float) match.length() / Math.min(s1.length(), s2.length());
	}

	protected String normalize(Object object) {
		// use string representation
		String name = object.toString();

		// normalize separators
		name = normalizePunctuation(name);

		// normalize case and trim
		return name.trim().toLowerCase();
	}

	protected String match(String s1, String s2) {
		return commonSequenceMatcher.matchFirstCommonSequence(s1, s2);
	}

}
