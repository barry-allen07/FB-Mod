package net.filebot.util;

import static net.filebot.Logging.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;

public class PreferencesMap<T> implements Map<String, T> {

	private final Preferences prefs;
	private final Adapter<T> adapter;

	public PreferencesMap(Preferences prefs, Adapter<T> adapter) {
		this.prefs = prefs;
		this.adapter = adapter;
	}

	@Override
	public T get(Object key) {
		return adapter.get(prefs, key.toString());
	}

	@Override
	public T put(String key, T value) {
		adapter.put(prefs, key, value);

		// don't know previous entry
		return null;
	}

	@Override
	public T remove(Object key) {
		adapter.remove(prefs, key.toString());

		// don't know removed entry
		return null;
	}

	public String[] keys() {
		try {
			return adapter.keys(prefs);
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void clear() {
		for (String key : keys()) {
			adapter.remove(prefs, key);
		}
	}

	@Override
	public boolean containsKey(Object key) {
		if (key instanceof String) {
			return Arrays.asList(keys()).contains(key);
		}

		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		for (String key : keys()) {
			if (value.equals(get(key)))
				return true;
		}

		return false;
	}

	@Override
	public Set<Entry<String, T>> entrySet() {
		Set<Map.Entry<String, T>> entries = new LinkedHashSet<Map.Entry<String, T>>();

		for (String key : keys()) {
			entries.add(new PreferencesEntry<T>(prefs, key, adapter));
		}

		return entries;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public Set<String> keySet() {
		return new LinkedHashSet<String>(Arrays.asList(keys()));
	}

	@Override
	public void putAll(Map<? extends String, ? extends T> map) {
		for (Map.Entry<? extends String, ? extends T> entry : map.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public int size() {
		return keys().length;
	}

	@Override
	public Collection<T> values() {
		List<T> values = new ArrayList<T>();

		for (String key : keys()) {
			values.add(get(key));
		}

		return values;
	}

	public static PreferencesMap<String> map(Preferences prefs) {
		return map(prefs, new StringAdapter());
	}

	public static <T> PreferencesMap<T> map(Preferences prefs, Adapter<T> adapter) {
		return new PreferencesMap<T>(prefs, adapter);
	}

	public static interface Adapter<T> {

		public String[] keys(Preferences prefs) throws BackingStoreException;

		public T get(Preferences prefs, String key);

		public void put(Preferences prefs, String key, T value);

		public void remove(Preferences prefs, String key);
	}

	public static abstract class AbstractAdapter<T> implements Adapter<T> {

		@Override
		public abstract T get(Preferences prefs, String key);

		@Override
		public abstract void put(Preferences prefs, String key, T value);

		@Override
		public String[] keys(Preferences prefs) throws BackingStoreException {
			return prefs.keys();
		}

		@Override
		public void remove(Preferences prefs, String key) {
			prefs.remove(key);
		}

	}

	public static class StringAdapter extends AbstractAdapter<String> {

		@Override
		public String get(Preferences prefs, String key) {
			return prefs.get(key, null);
		}

		@Override
		public void put(Preferences prefs, String key, String value) {
			prefs.put(key, value);
		}

	}

	public static class SimpleAdapter<T> extends AbstractAdapter<T> {

		private final Constructor<T> constructor;

		public SimpleAdapter(Class<T> type) {
			try {
				constructor = type.getConstructor(String.class);
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		}

		@Override
		public T get(Preferences prefs, String key) {
			String value = prefs.get(key, null);

			if (value != null) {
				try {
					return constructor.newInstance(value);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			return null;
		}

		@Override
		public void put(Preferences prefs, String key, T value) {
			prefs.put(key, value.toString());
		}

		public static <T> SimpleAdapter<T> forClass(Class<T> type) {
			return new SimpleAdapter<T>(type);
		}

	}

	public static class JsonAdapter<T> extends AbstractAdapter<T> {

		private Class<T> type;

		public JsonAdapter(Class<T> type) {
			this.type = type;
		}

		@Override
		public T get(Preferences prefs, String key) {
			String json = prefs.get(key, null);

			if (json != null) {
				try {
					return type.cast(JsonReader.jsonToJava(json));
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
				}
			}

			return null;
		}

		@Override
		public void put(Preferences prefs, String key, T value) {
			prefs.put(key, JsonWriter.objectToJson(value));
		}
	}

	public static class PreferencesEntry<T> implements Entry<String, T> {

		private final String key;

		private final Preferences prefs;

		private final Adapter<T> adapter;

		private T defaultValue = null;

		public PreferencesEntry(Preferences prefs, String key, Adapter<T> adapter) {
			this.key = key;
			this.prefs = prefs;
			this.adapter = adapter;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public T getValue() {
			T value = adapter.get(prefs, key);
			return value != null ? value : defaultValue;
		}

		@Override
		public T setValue(T value) {
			adapter.put(prefs, key, value);
			return null;
		}

		public PreferencesEntry<T> defaultValue(T defaultValue) {
			this.defaultValue = defaultValue;
			return this;
		}

		public void remove() {
			adapter.remove(prefs, key);
		}

		public void flush() {
			try {
				prefs.flush();
			} catch (Exception e) {
				debug.log(Level.WARNING, e.getMessage(), e);
			}
		}

	}

}
