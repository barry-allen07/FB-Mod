
package net.filebot.format;

import static net.filebot.util.ExceptionUtilities.*;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * Used to create a map view of the properties of an Object
 */
public class PropertyBindings extends AbstractMap<String, Object> {

	private final Object object;
	private final Map<String, Object> properties = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);

	public PropertyBindings(Object object) {
		this.object = object;

		// get method bindings
		for (Method method : object.getClass().getMethods()) {
			if (method.getReturnType() != void.class && method.getParameterTypes().length == 0 && !method.getDeclaringClass().getName().startsWith("java")) {
				// normal properties
				if (method.getName().length() > 3 && method.getName().substring(0, 3).equalsIgnoreCase("get")) {
					properties.put(method.getName().substring(3), method);
				}

				// boolean properties
				if (method.getName().length() > 2 && method.getName().substring(0, 2).equalsIgnoreCase("is")) {
					properties.put(method.getName().substring(2), method);
				}
			}
		}
	}

	@Override
	public Object get(Object key) {
		Object value = properties.get(key);

		// evaluate method
		if (value instanceof Method) {
			try {
				value = ((Method) value).invoke(object);
			} catch (Exception e) {
				throw new BindingException(key, getRootCauseMessage(e), e);
			}
		}

		return value;
	}

	@Override
	public Object put(String key, Object value) {
		return properties.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		return properties.remove(key);
	}

	@Override
	public boolean containsKey(Object key) {
		return properties.containsKey(key);
	}

	@Override
	public Set<String> keySet() {
		return properties.keySet();
	}

	@Override
	public boolean isEmpty() {
		return properties.isEmpty();
	}

	@Override
	public String toString() {
		return properties.toString();
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		Set<Entry<String, Object>> entrySet = new HashSet<Entry<String, Object>>();

		for (final String key : keySet()) {
			entrySet.add(new Entry<String, Object>() {

				@Override
				public String getKey() {
					return key;
				}

				@Override
				public Object getValue() {
					return get(key);
				}

				@Override
				public Object setValue(Object value) {
					return put(key, value);
				}
			});
		}

		return entrySet;
	}

}
