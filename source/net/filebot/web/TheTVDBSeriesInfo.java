package net.filebot.web;

import java.io.Serializable;
import java.net.URL;
import java.util.Locale;

public class TheTVDBSeriesInfo extends SeriesInfo implements Serializable {

	protected String slug;
	protected String imdbId;
	protected String overview;
	protected String airsDayOfWeek;
	protected String airsTime;
	protected URL banner;
	protected long lastUpdated;

	public TheTVDBSeriesInfo() {
		// used by deserializer
	}

	public TheTVDBSeriesInfo(TheTVDBSeriesInfo other) {
		super(other);
		this.slug = other.slug;
		this.imdbId = other.imdbId;
		this.overview = other.overview;
		this.airsDayOfWeek = other.airsDayOfWeek;
		this.airsTime = other.airsTime;
		this.banner = other.banner;
		this.lastUpdated = other.lastUpdated;
	}

	public TheTVDBSeriesInfo(Datasource database, Locale language, Integer id) {
		super(database, language, id);
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getImdbId() {
		return imdbId;
	}

	public void setImdbId(String imdbId) {
		this.imdbId = imdbId;
	}

	public String getOverview() {
		return overview;
	}

	public void setOverview(String overview) {
		this.overview = overview;
	}

	public String getAirsDayOfWeek() {
		return airsDayOfWeek;
	}

	public void setAirsDayOfWeek(String airsDayOfWeek) {
		this.airsDayOfWeek = airsDayOfWeek;
	}

	public String getAirsTime() {
		return airsTime;
	}

	public void setAirsTime(String airsTime) {
		this.airsTime = airsTime;
	}

	public URL getBannerUrl() {
		return banner;
	}

	public void setBannerUrl(URL banner) {
		this.banner = banner;
	}

	public long getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Long lastUpdated) {
		this.lastUpdated = lastUpdated == null ? 0 : lastUpdated;
	}

	@Override
	public TheTVDBSeriesInfo clone() {
		return new TheTVDBSeriesInfo(this);
	}

}
