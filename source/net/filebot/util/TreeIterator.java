
package net.filebot.util;


import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;


public abstract class TreeIterator<T> implements Iterator<T> {

	private final LinkedList<Iterator<T>> recursionStack = new LinkedList<Iterator<T>>();


	public TreeIterator(T... root) {
		recursionStack.push(Arrays.asList(root).iterator());
	}


	protected abstract Iterator<T> children(T node);


	@Override
	public boolean hasNext() {
		return currentIterator().hasNext();
	}


	@Override
	public T next() {
		T node = currentIterator().next();

		Iterator<T> children = children(node);
		if (children != null && children.hasNext()) {
			// step into next recursion level
			recursionStack.push(children);
		}

		return node;
	}


	private Iterator<T> currentIterator() {
		Iterator<T> iterator = recursionStack.peek();

		if (iterator.hasNext() || recursionStack.size() <= 1)
			return iterator;

		// step back one recursion level
		recursionStack.pop();

		return currentIterator();
	}


	@Override
	public void remove() {
		// can't just use remove() on current iterator, because
		// we may have stepped into the next recursion level
		throw new UnsupportedOperationException();
	}

}
