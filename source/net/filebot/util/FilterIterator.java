
package net.filebot.util;


import java.util.Iterator;


public abstract class FilterIterator<S, T> implements Iterator<T> {

	private final Iterator<S> sourceIterator;


	public FilterIterator(Iterable<S> source) {
		this(source.iterator());
	}


	public FilterIterator(Iterator<S> sourceIterator) {
		this.sourceIterator = sourceIterator;
	}


	@Override
	public boolean hasNext() {
		return peekNext(false) != null;
	}


	@Override
	public T next() {
		try {
			return peekNext(true);
		} finally {
			current = null;
		}
	}

	private T current = null;


	private T peekNext(boolean forceNext) {
		while (current == null && (forceNext || (sourceIterator.hasNext()))) {
			current = filter(sourceIterator.next());
		}

		return current;
	}


	protected abstract T filter(S sourceValue);


	@Override
	public void remove() {
		sourceIterator.remove();
	}

}
