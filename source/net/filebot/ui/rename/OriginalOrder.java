package net.filebot.ui.rename;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

class OriginalOrder<T> implements Comparator<T> {

	public static <T> Comparator<T> of(Collection<T> values) {
		return new OriginalOrder(values);
	}

	private Map<T, Integer> index;

	public OriginalOrder(Collection<T> values) {
		this.index = new HashMap<T, Integer>(values.size());

		int i = 0;
		for (T it : values) {
			index.put(it, i++);
		}
	}

	@Override
	public int compare(T o1, T o2) {
		Integer a = index.get(o1);
		Integer b = index.get(o2);

		if (a == null)
			return b == null ? 0 : 1;
		if (b == null)
			return -1;

		return a.compareTo(b);
	}

}
