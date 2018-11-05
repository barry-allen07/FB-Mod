package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;

import java.io.Serializable;
import java.net.URL;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import net.filebot.CachedResource.Transform;

public class MovieInfo implements Crew, Serializable {

	public enum Property {
		adult, backdrop_path, budget, homepage, id, imdb_id, original_title, original_language, overview, popularity, poster_path, release_date, revenue, runtime, tagline, title, vote_average, vote_count, certification, collection
	}

	protected Map<Property, String> fields;

	protected String[] alternativeTitles;
	protected String[] genres;
	protected String[] spokenLanguages;
	protected String[] productionCountries;
	protected String[] productionCompanies;
	protected Map<String, String> certifications;

	protected Person[] people;
	protected Trailer[] trailers;

	public MovieInfo() {
		// used by serializer
	}

	public MovieInfo(Map<Property, String> fields, List<String> alternativeTitles, List<String> genres, Map<String, String> certifications, List<String> spokenLanguages, List<String> productionCountries, List<String> productionCompanies, List<Person> people, List<Trailer> trailers) {
		this.fields = new EnumMap<Property, String>(fields);
		this.alternativeTitles = alternativeTitles.toArray(new String[0]);
		this.genres = genres.toArray(new String[0]);
		this.certifications = new LinkedHashMap<String, String>(certifications);
		this.spokenLanguages = spokenLanguages.toArray(new String[0]);
		this.productionCountries = productionCountries.toArray(new String[0]);
		this.productionCompanies = productionCompanies.toArray(new String[0]);
		this.people = people.toArray(new Person[0]);
		this.trailers = trailers.toArray(new Trailer[0]);
	}

	public String get(Object key) {
		return fields.get(Property.valueOf(key.toString()));
	}

	public String get(Property key) {
		return fields.get(key);
	}

	private <T> T get(Property key, Transform<String, T> cast) {
		try {
			String value = fields.get(key);
			if (value != null && !value.isEmpty()) {
				return cast.transform(value);
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, format("Failed to parse %s value: %s: %s", key, e, fields));
		}
		return null;
	}

	public String getName() {
		return get(Property.title);
	}

	public String getOriginalName() {
		return get(Property.original_title);
	}

	public String getOriginalLanguage() {
		return get(Property.original_language);
	}

	public String getCollection() {
		return get(Property.collection); // e.g. Star Wars Collection
	}

	public String getCertification() {
		return get(Property.certification); // e.g. PG-13
	}

	public boolean isAdult() {
		return get(Property.adult, Boolean::parseBoolean);
	}

	public String getTagline() {
		return get(Property.tagline);
	}

	public String getOverview() {
		return get(Property.overview);
	}

	public Integer getId() {
		return get(Property.id, Integer::parseInt);
	}

	public Integer getImdbId() {
		return get(Property.imdb_id, s -> Integer.parseInt(s.substring(2))); // e.g. tt0379786
	}

	public Integer getVotes() {
		return get(Property.vote_count, Integer::parseInt);
	}

	public Double getRating() {
		return get(Property.vote_average, Double::parseDouble);
	}

	public SimpleDate getReleased() {
		return get(Property.release_date, SimpleDate::parse); // e.g. 2005-09-30
	}

	public Integer getRuntime() {
		return get(Property.runtime, Integer::parseInt);
	}

	public Long getBudget() {
		return get(Property.budget, Long::parseLong);
	}

	public Long getRevenue() {
		return get(Property.revenue, Long::parseLong);
	}

	public Double getPopularity() {
		return get(Property.popularity, Double::parseDouble);
	}

	public URL getHomepage() {
		return get(Property.homepage, URL::new);
	}

	public URL getPoster() {
		return get(Property.poster_path, URL::new);
	}

	public List<String> getGenres() {
		return unmodifiableList(asList(genres));
	}

	public List<Locale> getSpokenLanguages() {
		return stream(spokenLanguages).map(Locale::new).collect(toList());
	}

	public List<Person> getCrew() {
		return unmodifiableList(asList(people));
	}

	public Map<String, String> getCertifications() {
		return unmodifiableMap(certifications); // e.g. ['US': PG-13]
	}

	public List<String> getProductionCountries() {
		return unmodifiableList(asList(productionCountries));
	}

	public List<String> getProductionCompanies() {
		return unmodifiableList(asList(productionCompanies));
	}

	public List<Trailer> getTrailers() {
		return unmodifiableList(asList(trailers));
	}

	public List<String> getAlternativeTitles() {
		return unmodifiableList(asList(alternativeTitles));
	}

	@Override
	public String toString() {
		return fields.toString();
	}

}