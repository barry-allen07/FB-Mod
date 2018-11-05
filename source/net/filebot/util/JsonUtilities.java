package net.filebot.util;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.Logging.*;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;

public class JsonUtilities {

	public static final Object[] EMPTY_ARRAY = new Object[0];

	public static Object readJson(CharSequence json) {
		if (json.length() == 0) {
			return EMPTY_MAP;
		}
		return JsonReader.jsonToJava(json.toString(), singletonMap(JsonReader.USE_MAPS, true));
	}

	public static String json(Object object, boolean pretty) {
		return JsonWriter.objectToJson(object, singletonMap(JsonWriter.PRETTY_PRINT, pretty));
	}

	public static Map<?, ?> asMap(Object node) {
		if (node instanceof Map) {
			return (Map<?, ?>) node;
		}
		return EMPTY_MAP;
	}

	public static Object[] asArray(Object node) {
		if (node instanceof JsonObject) {
			JsonObject<?, ?> jsonObject = (JsonObject<?, ?>) node;
			if (jsonObject.isArray()) {
				return jsonObject.getArray();
			}
		}
		if (node instanceof Object[]) {
			return (Object[]) node;
		}
		return EMPTY_ARRAY;
	}

	public static Map<?, ?>[] asMapArray(Object node) {
		return stream(asArray(node)).map(JsonUtilities::asMap).filter(m -> m.size() > 0).toArray(Map[]::new);
	}

	public static Stream<Map<?, ?>> streamJsonObjects(Object node) {
		return stream(asMapArray(node));
	}

	public static Object[] getArray(Object node, String key) {
		return asArray(asMap(node).get(key));
	}

	public static Map<?, ?> getMap(Object node, String key) {
		return asMap(asMap(node).get(key));
	}

	public static Map<?, ?>[] getMapArray(Object node, String key) {
		return asMapArray(asMap(node).get(key));
	}

	public static Stream<Map<?, ?>> streamJsonObjects(Object node, String key) {
		return stream(getMapArray(node, key));
	}

	public static Map<?, ?> getFirstMap(Object node, String key) {
		Object[] values = getArray(node, key);
		if (values.length > 0) {
			return asMap(values[0]);
		}
		return EMPTY_MAP;
	}

	public static String getString(Object node, String key) {
		return StringUtilities.asNonEmptyString(asMap(node).get(key));
	}

	public static Integer getInteger(Object node, String key) {
		return getStringValue(node, key, Integer::parseInt);
	}

	public static Double getDouble(Object node, String key) {
		return getStringValue(node, key, Double::parseDouble);
	}

	public static BigDecimal getDecimal(Object node, String key) {
		return getStringValue(node, key, BigDecimal::new);
	}

	public static <V> V getStringValue(Object node, String key, Function<String, V> converter) {
		String value = getString(node, key);
		if (value != null) {
			try {
				return converter.apply(getString(node, key));
			} catch (Exception e) {
				debug.warning(format("Bad %s value: %s => %s", key, value, e));
			}
		}
		return null;
	}

	public static <K extends Enum<K>> EnumMap<K, String> getEnumMap(Object node, Class<K> cls) {
		return getEnumMap(node, cls, StringUtilities::asNonEmptyString);
	}

	public static <K extends Enum<K>, V> EnumMap<K, V> getEnumMap(Object node, Class<K> cls, Function<Object, V> converter) {
		Map<?, ?> values = asMap(node);
		EnumMap<K, V> map = new EnumMap<K, V>(cls);
		for (K key : cls.getEnumConstants()) {
			Object object = values.get(key.name());
			if (object != null) {
				V value = converter.apply(object);
				if (value != null) {
					map.put(key, value);
				}
			}
		}
		return map;
	}

	private JsonUtilities() {
		throw new UnsupportedOperationException();
	}

}
