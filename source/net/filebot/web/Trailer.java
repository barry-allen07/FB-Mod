package net.filebot.web;

import java.io.Serializable;

public class Trailer implements Serializable {

	protected String type;
	protected String name;
	protected String site;
	protected String key;
	protected Integer size;
	protected String language;

	public Trailer() {
		// used by serializer
	}

	public Trailer(String type, String name, String site, String key, Integer size, String language) {
		this.type = type;
		this.name = name;
		this.site = site;
		this.key = key;
		this.size = size;
		this.language = language;
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public Integer getSize() {
		return size;
	}

	public String getKey() {
		return key;
	}

	public String getLanguage() {
		return language;
	}

	@Override
	public String toString() {
		return String.format("%s [%s] [%s] [%s] [%s::%s]", name, type, size, language, site, key);
	}

}
