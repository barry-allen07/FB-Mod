package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class EpisodeInfo implements Crew, Serializable {

	protected String database;
	protected Integer seriesId;
	protected Integer id;
	protected String language;

	protected Person[] people;
	protected String overview;

	protected Double rating;
	protected Integer votes;

	public EpisodeInfo() {
		// used by deserializer
	}

	public EpisodeInfo(EpisodeInfo other) {
		database = other.database;
		seriesId = other.seriesId;
		id = other.id;
		language = other.language;
		people = other.people == null ? null : other.people.clone();
		overview = other.overview;
		rating = other.rating;
		votes = other.votes;
	}

	public EpisodeInfo(Datasource database, Locale language, Integer seriesId, Integer id, List<Person> people, String overview, Double rating, Integer votes) {
		this.database = database.getIdentifier();
		this.language = language.getLanguage();
		this.seriesId = seriesId;
		this.id = id;
		this.people = people.toArray(new Person[0]);
		this.overview = overview;
		this.votes = votes;
		this.rating = rating;
	}

	public String getDatabase() {
		return database;
	}

	public Integer getSeriesId() {
		return seriesId;
	}

	public Integer getId() {
		return id;
	}

	public String getLanguage() {
		return language;
	}

	public List<Person> getCrew() {
		return unmodifiableList(asList(people));
	}

	public String getOverview() {
		return overview;
	}

	public Double getRating() {
		return rating;
	}

	public Integer getVotes() {
		return votes;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof EpisodeInfo) {
			EpisodeInfo other = (EpisodeInfo) obj;
			return Objects.equals(id, other.id) && Objects.equals(database, other.database);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id;
	}

	@Override
	public EpisodeInfo clone() {
		return new EpisodeInfo(this);
	}

	@Override
	public String toString() {
		return database + "::" + seriesId + "::" + id;
	}

}
