package net.filebot;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.CachedResource.*;
import static net.filebot.Logging.*;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.w3c.dom.Document;

import net.filebot.CachedResource.Transform;
import net.sf.ehcache.Element;

public class Cache {

	// grace period to make sure data is always fresh when TTL is almost about to be reached
	public static final Duration ONE_DAY = Duration.ofHours(18);
	public static final Duration ONE_WEEK = Duration.ofDays(6);
	public static final Duration ONE_MONTH = Duration.ofDays(24);

	public static Cache getCache(String name, CacheType type) {
		return CacheManager.getInstance().getCache(name.toLowerCase() + "_" + type.ordinal(), type);
	}

	public <T> CachedResource<T, byte[]> bytes(T key, Transform<T, URL> resource) {
		return new CachedResource<T, byte[]>(key, resource, fetchIfModified(), getBytes(), byte[].class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, byte[]> bytes(T key, Transform<T, URL> resource, Transform<InputStream, InputStream> decompressor) {
		return new CachedResource<T, byte[]>(key, resource, fetchIfModified(), getBytes(decompressor), byte[].class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, String> text(T key, Transform<T, URL> resource) {
		return new CachedResource<T, String>(key, resource, fetchIfModified(), getText(UTF_8), String.class::cast, ONE_DAY, this);
	}

	public <T> CachedResource<T, Document> xml(T key, Transform<T, URL> resource) {
		return new CachedResource<T, Document>(key, resource, fetchIfModified(), validateXml(getText(UTF_8)), getXml(String.class::cast), ONE_DAY, this);
	}

	public <T> CachedResource<T, Object> json(T key, Transform<T, URL> resource) {
		return new CachedResource<T, Object>(key, resource, fetchIfModified(), validateJson(getText(UTF_8)), getJson(String.class::cast), ONE_DAY, this);
	}

	private final net.sf.ehcache.Cache cache;
	private final CacheType cacheType;

	public Cache(net.sf.ehcache.Cache cache, CacheType cacheType) {
		this.cache = cache;
		this.cacheType = cacheType;
	}

	public String getName() {
		return cache.getName();
	}

	public CacheType getCacheType() {
		return cacheType;
	}

	public Object get(Object key) {
		try {
			return getElementValue(cache.get(key));
		} catch (Exception e) {
			debug.warning(format("Cache get: %s => %s", key, e));
		}
		return null;
	}

	public Object computeIf(Object key, Predicate<Element> condition, Compute<?> compute) throws Exception {
		// get if present
		Element element = null;
		try {
			element = cache.get(key);
			if (element != null && !condition.test(element)) {
				return getElementValue(element);
			}
		} catch (Exception e) {
			debug.warning(format("Cache computeIf: %s => %s", key, e));
		}

		// compute if absent
		Object value = compute.apply(element);
		put(key, value);
		return value;
	}

	public Object computeIfAbsent(Object key, Compute<?> compute) throws Exception {
		return computeIf(key, it -> it == null, compute);
	}

	public void put(Object key, Object value) {
		try {
			cache.put(createElement(key, value));
		} catch (Exception e) {
			debug.warning(format("Cache put: %s => %s", key, e));
		}
	}

	protected Object getElementValue(Element element) {
		return element == null ? null : element.getObjectValue();
	}

	protected Element createElement(Object key, Object value) {
		return new Element(key, value);
	}

	public void remove(Object key) {
		try {
			cache.remove(key);
		} catch (Exception e) {
			debug.warning(format("Cache remove: %s => %s", key, e));
		}
	}

	public void flush() {
		try {
			cache.flush();
		} catch (Exception e) {
			debug.warning(format("Cache flush: %s => %s", cache.getName(), e));
		}
	}

	@Override
	public String toString() {
		return cache.getName();
	}

	public static Predicate<Element> isStale(Duration expirationTime) {
		return element -> System.currentTimeMillis() - element.getLatestOfCreationAndUpdateTime() > expirationTime.toMillis();
	}

	@FunctionalInterface
	public interface Compute<R> {
		R apply(Element element) throws Exception;
	}

	public <V> TypedCache<V> typed(Function<Object, V> read, Function<V, Object> write) {
		return new TypedCache<V>(cache, cacheType, read, write);
	}

	public <V> TypedCache<V> cast(Class<V> cls) {
		return new TypedCache<V>(cache, cacheType, it -> cls.cast(it), it -> it);
	}

	public <V> TypedCache<List<V>> castList(Class<V> cls) {
		return new TypedCache<List<V>>(cache, cacheType, it -> it == null ? null : stream((Object[]) it).map(cls::cast).collect(toList()), it -> it == null ? null : it.toArray());
	}

	public static class TypedCache<V> extends Cache {

		private final Function<Object, V> read;
		private final Function<V, Object> write;

		public TypedCache(net.sf.ehcache.Cache cache, CacheType cacheType, Function<Object, V> read, Function<V, Object> write) {
			super(cache, cacheType);
			this.read = read;
			this.write = write;
		}

		@Override
		public V get(Object key) {
			return (V) super.get(key);
		}

		@Override
		public V computeIf(Object key, Predicate<Element> condition, Compute<?> compute) throws Exception {
			return (V) super.computeIf(key, condition, compute);
		}

		@Override
		public V computeIfAbsent(Object key, Compute<?> compute) throws Exception {
			return (V) super.computeIfAbsent(key, compute);
		}

		@Override
		protected Object getElementValue(Element element) {
			return read.apply(super.getElementValue(element));
		}

		@Override
		protected Element createElement(Object key, Object value) {
			return super.createElement(key, write.apply((V) value));
		}
	}

}
