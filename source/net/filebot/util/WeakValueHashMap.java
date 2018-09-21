package net.filebot.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class WeakValueHashMap<K, V> extends AbstractMap<K, V> {

	private HashMap<K, WeakValue<V>> references;
	private ReferenceQueue<V> gcQueue;

	public WeakValueHashMap(int capacity) {
		references = new HashMap<K, WeakValue<V>>(capacity);
		gcQueue = new ReferenceQueue<V>();
	}

	@Override
	public V put(K key, V value) {
		processQueue();

		return getReferenceValue(references.put(key, new WeakValue<V>(key, value, gcQueue)));
	};

	@Override
	public V get(Object key) {
		processQueue();

		return getReferenceValue(references.get(key));
	}

	@Override
	public V remove(Object key) {
		return getReferenceValue(references.remove(key));
	}

	@Override
	public void clear() {
		references.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		processQueue();

		return references.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		processQueue();

		for (WeakValue<V> it : references.values()) {
			if (value.equals(getReferenceValue(it))) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Set<K> keySet() {
		processQueue();

		return references.keySet();
	}

	@Override
	public int size() {
		processQueue();

		return references.size();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		processQueue();

		Set<Entry<K, V>> entries = new LinkedHashSet<Entry<K, V>>(references.size());
		for (Entry<K, WeakValue<V>> entry : references.entrySet()) {
			entries.add(new SimpleImmutableEntry<K, V>(entry.getKey(), getReferenceValue(entry.getValue())));
		}

		return entries;
	}

	@Override
	public Collection<V> values() {
		processQueue();

		Collection<V> values = new ArrayList<V>(references.size());
		for (WeakValue<V> valueRef : references.values()) {
			values.add(getReferenceValue(valueRef));
		}

		return values;
	}

	private V getReferenceValue(WeakValue<V> valueRef) {
		return valueRef == null ? null : valueRef.get();
	}

	private void processQueue() {
		WeakValue<?> valueRef;
		while ((valueRef = (WeakValue<?>) gcQueue.poll()) != null) {
			references.remove(valueRef.getKey());
		}
	}

	private class WeakValue<T> extends WeakReference<T> {

		private final K key;

		private WeakValue(K key, T value, ReferenceQueue<T> queue) {
			super(value, queue);
			this.key = key;
		}

		private K getKey() {
			return key;
		}
	}

}
