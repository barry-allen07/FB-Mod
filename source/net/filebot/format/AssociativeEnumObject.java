package net.filebot.format;

import static net.filebot.util.RegularExpressions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import groovy.lang.GroovyObjectSupport;

public class AssociativeEnumObject extends GroovyObjectSupport implements List<Object> {

	private final Map<?, ?> values;

	public AssociativeEnumObject(Map<?, ?> values) {
		this.values = values;
	}

	protected String definingKey(Object key) {
		// letters and digits are defining, everything else will be ignored
		return NON_WORD.matcher(key.toString()).replaceAll("").toLowerCase();
	}

	@Override
	public Object getProperty(String name) {
		return getValue(definingKey(name)).orElseGet(() -> super.getProperty(name));
	}

	private Optional<Object> getValue(String key) {
		return values.keySet().stream().filter(k -> key.equals(definingKey(k))).findFirst().map(values::get).map(Object.class::cast);
	}

	@Override
	public void setProperty(String name, Object value) {
		throw new UnsupportedOperationException();
	}

	public Set<?> keySet() {
		return values.keySet();
	}

	@Override
	public String toString() {
		return values.values().toString();
	}

	public List<Object> toList() {
		return new ArrayList<Object>(values.values());
	}

	@Override
	public Iterator<Object> iterator() {
		return toList().iterator();
	}

	@Override
	public Object get(int index) {
		return toList().get(index);
	}

	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		return toList().subList(fromIndex, toIndex);
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return values.values().contains(o);
	}

	@Override
	public Object[] toArray() {
		return values.values().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return values.values().toArray(a);
	}

	@Override
	public boolean add(Object e) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return values.values().containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Object> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(int index, Collection<? extends Object> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		return toList().indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return toList().lastIndexOf(o);
	}

	@Override
	public ListIterator<Object> listIterator() {
		return toList().listIterator();
	}

	@Override
	public ListIterator<Object> listIterator(int index) {
		return toList().listIterator(index);
	}

}
