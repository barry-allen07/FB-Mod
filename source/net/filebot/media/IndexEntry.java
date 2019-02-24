package net.filebot.media;

import java.io.Serializable;
import java.text.CollationKey;

class IndexEntry<T> implements Serializable {

	private T object;
	private String lenientName;
	private String strictName;

	private transient CollationKey[] lenientKey;
	private transient CollationKey[] strictKey;

	public IndexEntry(T object, String lenientName, String strictName) {
		this.object = object;
		this.lenientName = lenientName;
		this.strictName = strictName;
	}

	public T getObject() {
		return object;
	}

	public String getLenientName() {
		return lenientName;
	}

	public String getStrictName() {
		return strictName;
	}

	public CollationKey[] getLenientKey() {
		if (lenientKey == null && lenientName != null) {
			lenientKey = HighPerformanceMatcher.prepare(lenientName);
		}
		return lenientKey;
	}

	public CollationKey[] getStrictKey() {
		if (strictKey == null && strictName != null) {
			strictKey = HighPerformanceMatcher.prepare(strictName);
		}
		return strictKey;
	}

	@Override
	public String toString() {
		return strictName != null ? strictName : lenientName;
	}

}
