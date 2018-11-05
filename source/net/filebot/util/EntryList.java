package net.filebot.util;

import static java.util.Collections.*;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EntryList<K, V> extends AbstractMap<K, V> {

	private List<? extends K> keys;
	private List<? extends V> values;

	public EntryList(List<? extends K> keys, List<? extends V> values) {
		this.keys = keys != null ? keys : emptyList();
		this.values = values != null ? values : emptyList();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<Entry<K, V>>() {

			@Override
			public Iterator<Entry<K, V>> iterator() {
				return new Iterator<Entry<K, V>>() {

					private Iterator<? extends K> keySeq = keys.iterator();
					private Iterator<? extends V> valueSeq = values.iterator();

					@Override
					public boolean hasNext() {
						return keySeq.hasNext() || valueSeq.hasNext();
					}

					@Override
					public Entry<K, V> next() {
						K key = keySeq.hasNext() ? keySeq.next() : null;
						V value = valueSeq.hasNext() ? valueSeq.next() : null;
						return new SimpleImmutableEntry<K, V>(key, value);
					}
				};
			}

			@Override
			public int size() {
				return keys.size();
			}
		};
	}

	@Override
	public Set<K> keySet() {
		return new AbstractSet<K>() {

			@Override
			public Iterator<K> iterator() {
				return (Iterator<K>) keys.iterator();
			}

			@Override
			public int size() {
				return keys.size();
			}
		};
	}

	@Override
	public List<V> values() {
		return (List<V>) values;
	}

	@Override
	public int size() {
		return Math.max(keys.size(), values.size());
	}

}
