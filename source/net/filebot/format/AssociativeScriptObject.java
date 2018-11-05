
package net.filebot.format;

import static net.filebot.util.RegularExpressions.*;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import groovy.lang.GroovyObjectSupport;

public class AssociativeScriptObject extends GroovyObjectSupport implements Iterable<Entry<Object, Object>> {

	private final Map<Object, Object> properties;

	private final Function<String, Object> defaultValue;

	public AssociativeScriptObject(Map<?, ?> properties) {
		this.properties = new LenientLookup(properties);

		// throw MissingPropertyException
		this.defaultValue = super::getProperty;
	}

	public AssociativeScriptObject(Map<?, ?> properties, Function<String, Object> defaultValue) {
		this.properties = new LenientLookup(properties);
		this.defaultValue = defaultValue;
	}

	@Override
	public Object getProperty(String name) {
		Object value = properties.get(name);

		if (value == null) {
			return defaultValue.apply(name);
		}

		return value;
	}

	public Object getDefaultProperty(String name) {
		return super.getProperty(name);
	}

	@Override
	public void setProperty(String name, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Entry<Object, Object>> iterator() {
		return properties.entrySet().iterator();
	}

	@Override
	public String toString() {
		// all the properties in alphabetic order
		return properties.keySet().toString();
	}

	/**
	 * Map allowing look-up of values by a fault-tolerant key as specified by the defining key.
	 *
	 */
	private static class LenientLookup extends AbstractMap<Object, Object> {

		private final Map<String, Entry<?, ?>> lookup = new LinkedHashMap<String, Entry<?, ?>>();

		public LenientLookup(Map<?, ?> source) {
			// populate lookup map
			for (Entry<?, ?> entry : source.entrySet()) {
				lookup.put(definingKey(entry.getKey()), entry);
			}
		}

		protected String definingKey(Object key) {
			// letters and digits are defining, everything else will be ignored
			return NON_WORD.matcher(key.toString()).replaceAll("").toLowerCase();
		}

		@Override
		public boolean containsKey(Object key) {
			return lookup.containsKey(definingKey(key));
		}

		@Override
		public Object get(Object key) {
			Entry<?, ?> entry = lookup.get(definingKey(key));

			if (entry != null)
				return entry.getValue();

			return null;
		}

		@Override
		public Set<Entry<Object, Object>> entrySet() {
			return new AbstractSet<Entry<Object, Object>>() {

				@Override
				public Iterator<Entry<Object, Object>> iterator() {
					return (Iterator) lookup.values().iterator();
				}

				@Override
				public int size() {
					return lookup.size();
				}
			};
		}
	}

}
