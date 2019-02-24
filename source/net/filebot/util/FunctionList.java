package net.filebot.util;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Function;

public class FunctionList<S, E> extends AbstractList<E> {

	private List<S> source;
	private Function<S, E> function;

	public FunctionList(List<S> source, Function<S, E> function) {
		this.source = source;
		this.function = function;
	}

	@Override
	public E get(int index) {
		return function.apply(source.get(index));
	}

	@Override
	public int size() {
		return source.size();
	}

}
