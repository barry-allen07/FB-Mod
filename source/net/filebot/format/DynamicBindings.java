package net.filebot.format;

import java.util.Collection;
import java.util.function.Supplier;

import groovy.lang.GroovyObjectSupport;

public class DynamicBindings extends GroovyObjectSupport {

	private Supplier<Collection<?>> keys;
	private Get<String, Object> properties;

	public DynamicBindings(Supplier<Collection<?>> keys, Get<String, Object> properties) {
		this.keys = keys;
		this.properties = properties;
	}

	@Override
	public Object getProperty(String property) {
		try {
			return properties.get(property);
		} catch (Exception e) {
			if (e instanceof BindingException) {
				throw (BindingException) e;
			}
			throw new BindingException(property, e.getMessage(), e);
		}
	}

	@Override
	public String toString() {
		return keys.get().toString();
	}

	@FunctionalInterface
	public interface Get<T, R> {
		R get(T t) throws Exception;
	}

}
