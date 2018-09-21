package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static net.filebot.similarity.Normalization.*;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.ibm.icu.text.Transliterator;

import uk.ac.shef.wit.simmetrics.similaritymetrics.AbstractStringMetric;
import uk.ac.shef.wit.simmetrics.similaritymetrics.QGramsDistance;

public class LocalSearch<T> {

	private AbstractStringMetric metric = new QGramsDistance();
	private float resultMinimumSimilarity = 0.5f;
	private int resultSetSize = 20;

	private Transliterator transliterator = Transliterator.getInstance("Any-Latin;Latin-ASCII;[:Diacritic:]remove");

	private T[] objects;
	private Set<String>[] fields;

	public LocalSearch(T[] data, Function<T, Collection<String>> keywords) {
		objects = data.clone();
		fields = stream(objects).map(keywords).map(this::normalize).toArray(Set[]::new);
	}

	public List<T> search(String q) throws ExecutionException, InterruptedException {
		String query = normalize(q);

		return IntStream.range(0, objects.length).mapToObj(i -> {
			T object = objects[i];
			Set<String> field = fields[i];

			boolean match = field.stream().anyMatch(it -> it.contains(query));
			double similarity = field.stream().mapToDouble(it -> metric.getSimilarity(query, it)).max().orElse(0);

			return match || similarity > resultMinimumSimilarity ? new SimpleImmutableEntry<T, Double>(object, similarity) : null;
		}).filter(Objects::nonNull).sorted(reverseOrder(comparing(Entry::getValue))).limit(resultSetSize).map(Entry::getKey).collect(toList());
	}

	public void setResultMinimumSimilarity(float resultMinimumSimilarity) {
		this.resultMinimumSimilarity = resultMinimumSimilarity;
	}

	public void setResultSetSize(int resultSetSize) {
		this.resultSetSize = resultSetSize;
	}

	protected Set<String> normalize(Collection<String> values) {
		return values.stream().map(this::normalize).collect(toSet());
	}

	protected String normalize(String value) {
		// normalize separator, trim and normalize case
		return normalizePunctuation(transliterator.transform(value)).toLowerCase();
	}

}
