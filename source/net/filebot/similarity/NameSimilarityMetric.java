
package net.filebot.similarity;


import static net.filebot.similarity.Normalization.*;

import com.ibm.icu.text.Transliterator;

import uk.ac.shef.wit.simmetrics.similaritymetrics.AbstractStringMetric;
import uk.ac.shef.wit.simmetrics.similaritymetrics.QGramsDistance;
import uk.ac.shef.wit.simmetrics.tokenisers.TokeniserQGram3;


public class NameSimilarityMetric implements SimilarityMetric {

	private final AbstractStringMetric metric;
	private final Transliterator transliterator;


	public NameSimilarityMetric() {
		// QGramsDistance with a QGram tokenizer seems to work best for similarity of names
		this(new QGramsDistance(new TokeniserQGram3()), Transliterator.getInstance("Any-Latin;Latin-ASCII;[:Diacritic:]remove"));
	}


	public NameSimilarityMetric(AbstractStringMetric metric, Transliterator transliterator) {
		this.metric = metric;
		this.transliterator = transliterator;
	}


	@Override
	public float getSimilarity(Object o1, Object o2) {
		return metric.getSimilarity(normalize(o1), normalize(o2));
	}


	protected String normalize(Object object) {
		// use string representation
		String name = object.toString();

		// apply transliterator
		if (transliterator != null) {
			name = transliterator.transform(name);
		}

		// normalize separators
		name = normalizePunctuation(name);

		// normalize case and trim
		return name.toLowerCase();
	}

}
