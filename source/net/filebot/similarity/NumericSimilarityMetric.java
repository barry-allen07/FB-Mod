package net.filebot.similarity;

import static java.util.stream.Collectors.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.StringUtilities.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import uk.ac.shef.wit.simmetrics.similaritymetrics.AbstractStringMetric;
import uk.ac.shef.wit.simmetrics.similaritymetrics.QGramsDistance;
import uk.ac.shef.wit.simmetrics.tokenisers.InterfaceTokeniser;
import uk.ac.shef.wit.simmetrics.wordhandlers.DummyStopTermHandler;
import uk.ac.shef.wit.simmetrics.wordhandlers.InterfaceTermHandler;

public class NumericSimilarityMetric implements SimilarityMetric {

	private final AbstractStringMetric metric;

	public NumericSimilarityMetric() {
		// I don't exactly know why, but I get a good matching behavior
		// when using QGramsDistance or BlockDistance
		metric = new QGramsDistance(new NumberTokeniser());
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		return metric.getSimilarity(normalize(o1), normalize(o2));
	}

	protected String normalize(Object object) {
		// no need to do anything special here, because we don't care about anything but number patterns anyway
		return object.toString();
	}

	private static class NumberTokeniser implements InterfaceTokeniser {

		@Override
		public ArrayList<String> tokenizeToArrayList(String s) {
			return matchIntegers(s).stream().map(String::valueOf).collect(toCollection(ArrayList::new));
		}

		@Override
		public String getDelimiters() {
			return NON_DIGIT.pattern();
		}

		@Override
		public Set<String> tokenizeToSet(String input) {
			return new LinkedHashSet<String>(tokenizeToArrayList(input));
		}

		@Override
		public String getShortDescriptionString() {
			return getClass().getSimpleName();
		}

		private InterfaceTermHandler stopWordHandler = new DummyStopTermHandler();

		@Override
		public InterfaceTermHandler getStopWordHandler() {
			return stopWordHandler;
		}

		@Override
		public void setStopWordHandler(InterfaceTermHandler stopWordHandler) {
			this.stopWordHandler = stopWordHandler;
		}

	}

}
