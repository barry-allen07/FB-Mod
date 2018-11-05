
package net.filebot.util;


import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.prefs.Preferences;

import net.filebot.util.PreferencesMap.Adapter;


public class PreferencesList<T> extends AbstractList<T> implements RandomAccess {

	private final PreferencesMap<T> prefs;


	public PreferencesList(PreferencesMap<T> preferencesMap) {
		this.prefs = preferencesMap;
	}


	@Override
	public T get(int index) {
		return prefs.get(key(index));
	}


	private String key(int index) {
		return Integer.toString(index);
	}


	@Override
	public int size() {
		return prefs.size();
	}


	@Override
	public boolean add(T e) {
		setImpl(size(), e);
		return true;
	}


	@Override
	public void add(int index, T element) {
		int size = size();

		if (index > size)
			throw new IndexOutOfBoundsException(String.format("Index: %d, Size: %d", index, size));

		copy(index, index + 1, size - index);

		setImpl(index, element);
	}


	private T setImpl(int index, T element) {
		return prefs.put(key(index), element);
	}


	/**
	 * @return always null
	 */
	@Override
	public T remove(int index) {
		int lastIndex = size() - 1;

		copy(index + 1, index, lastIndex - index);
		prefs.remove(key(lastIndex));

		return null;
	}


	@Override
	public T set(int index, T element) {
		if (index < 0 || index >= size())
			throw new IndexOutOfBoundsException();

		return setImpl(index, element);
	}


	private void copy(int startIndex, int newStartIndex, int count) {
		if (count == 0 || startIndex == newStartIndex)
			return;

		List<T> copy = new ArrayList<T>(subList(startIndex, startIndex + count));

		for (int i = newStartIndex, n = 0; n < count; i++, n++) {
			setImpl(i, copy.get(n));
		}
	}


	public void trimToSize(int limit) {
		for (int i = size() - 1; i >= limit; i--) {
			remove(i);
		}
	}


	public void set(Collection<T> data) {
		// remove all elements beyond data.size
		trimToSize(data.size());

		// override elements
		int i = 0;
		for (T element : data) {
			setImpl(i++, element);
		}
	}


	@Override
	public void clear() {
		prefs.clear();
	}


	public static PreferencesList<String> map(Preferences prefs) {
		return new PreferencesList<String>(PreferencesMap.map(prefs));
	}


	public static <T> PreferencesList<T> map(Preferences prefs, Adapter<T> adapter) {
		return new PreferencesList<T>(PreferencesMap.map(prefs, adapter));
	}

}
