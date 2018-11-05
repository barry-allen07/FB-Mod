package net.filebot.web;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.CachedResource.*;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;

public class OMDbClient implements MovieIdentificationService {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(2, 1, TimeUnit.SECONDS);

	private String apikey;

	public OMDbClient(String apikey) {
		this.apikey = apikey;
	}

	@Override
	public String getIdentifier() {
		return "OMDb";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.omdb");
	}

	protected int getImdbId(String link) {
		Matcher matcher = Pattern.compile("tt(\\d{7})").matcher(link);

		if (matcher.find()) {
			return Integer.parseInt(matcher.group(1));
		}

		// pattern not found
		throw new IllegalArgumentException(String.format("Cannot find imdb id: %s", link));
	}

	@Override
	public List<Movie> searchMovie(String query, Locale locale) throws Exception {
		// query by name with year filter if possible
		Matcher nameYear = Pattern.compile("(.+)\\b(19\\d{2}|20\\d{2})$").matcher(query);
		if (nameYear.matches()) {
			return searchMovie(nameYear.group(1).trim(), Integer.parseInt(nameYear.group(2)));
		} else {
			return searchMovie(query, -1);
		}
	}

	public List<Movie> searchMovie(String movieName, int movieYear) throws Exception {
		Map<String, Object> param = new LinkedHashMap<String, Object>(2);
		param.put("s", movieName);
		if (movieYear > 0) {
			param.put("y", movieYear);
		}

		Object response = request(param);

		List<Movie> result = new ArrayList<Movie>();
		for (Object it : getArray(response, "Search")) {
			Map<String, String> info = getInfoMap(it);
			if ("movie".equals(info.get("Type"))) {
				result.add(getMovie(info));
			}
		}
		return result;
	}

	@Override
	public Movie getMovieDescriptor(Movie id, Locale locale) throws Exception {
		if (id.getImdbId() <= 0) {
			throw new IllegalArgumentException("Illegal ID: " + id.getImdbId());
		}

		// request full movie info for given id
		return getMovie(getMovieInfo(id.getImdbId(), null, null, false));
	}

	public Map<String, String> getInfoMap(Object node) {
		Map<String, String> info = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		if (node instanceof Map) {
			for (Entry<?, ?> it : ((Map<?, ?>) node).entrySet()) {
				if (it.getKey() != null && it.getValue() != null) {
					info.put(it.getKey().toString().trim(), it.getValue().toString().trim());
				}
			}
		}
		return info;
	}

	public Movie getMovie(Map<String, String> info) {
		try {
			String name = info.get("Title");
			int year = matchInteger(info.get("Year"));
			int imdbid = Integer.parseInt(info.get("imdbID").replace("tt", ""));

			if (name.length() <= 0 || year <= 1900 || imdbid <= 0)
				throw new IllegalArgumentException();

			return new Movie(name, year, imdbid);
		} catch (Exception e) {
			throw new IllegalArgumentException("Illegal fields: " + info);
		}
	}

	public Object request(Map<String, Object> parameters) throws Exception {
		Cache cache = Cache.getCache(getName(), CacheType.Monthly);

		return cache.json(encodeParameters(parameters, true), s -> {
			return getResource('?' + s + "&apikey=" + apikey);
		}).fetch(withPermit(fetchIfModified(), r -> REQUEST_LIMIT.acquirePermit())).expire(Cache.ONE_WEEK).get();
	}

	public URL getResource(String file) throws Exception {
		return new URL("https://private.omdbapi.com/" + file);
	}

	public Map<String, String> getMovieInfo(Integer i, String t, String y, boolean tomatoes) throws Exception {
		// e.g. http://www.imdbapi.com/?i=tt0379786&r=xml&tomatoes=true
		Map<String, Object> param = new LinkedHashMap<String, Object>(2);
		if (i != null) {
			param.put("i", String.format("tt%07d", i));
		}
		if (t != null) {
			param.put("t", t);
		}
		if (y != null) {
			param.put("y", y);
		}
		param.put("tomatoes", String.valueOf(tomatoes));

		return getInfoMap(request(param));
	}

	public MovieInfo getMovieInfo(Movie movie) throws Exception {
		Map<String, String> data = movie.getImdbId() > 0 ? getMovieInfo(movie.getImdbId(), null, null, false) : getMovieInfo(null, movie.getName(), String.valueOf(movie.getYear()), false);

		// sanity check
		if (!Boolean.parseBoolean(data.get("response"))) {
			throw new IllegalArgumentException("Movie not found: " + data);
		}

		Map<MovieInfo.Property, String> fields = new EnumMap<MovieInfo.Property, String>(MovieInfo.Property.class);
		fields.put(MovieInfo.Property.title, data.get("title"));
		fields.put(MovieInfo.Property.certification, data.get("rated"));
		fields.put(MovieInfo.Property.runtime, getRuntimeMinutes(data.get("runtime")));
		fields.put(MovieInfo.Property.tagline, data.get("plot"));
		fields.put(MovieInfo.Property.vote_average, data.get("imdbRating"));
		fields.put(MovieInfo.Property.vote_count, getVoteCount(data.get("imdbVotes")));
		fields.put(MovieInfo.Property.imdb_id, data.get("imdbID"));
		fields.put(MovieInfo.Property.poster_path, data.get("poster"));
		fields.put(MovieInfo.Property.release_date, getReleaseDate(data.get("released")));

		// convert lists
		Pattern delim = Pattern.compile(",");
		List<String> genres = split(delim, data.get("genre"), String::toString);
		List<String> languages = split(delim, data.get("language"), String::toString);

		List<Person> actors = new ArrayList<Person>();
		actors.addAll(split(delim, data.get("actors"), s -> new Person(s, Person.ACTOR)));
		actors.addAll(split(delim, data.get("director"), s -> new Person(s, Person.DIRECTOR)));
		actors.addAll(split(delim, data.get("writer"), s -> new Person(s, Person.WRITER)));

		return new MovieInfo(fields, emptyList(), genres, emptyMap(), languages, emptyList(), emptyList(), actors, emptyList());
	}

	private String getVoteCount(String votes) {
		return NON_DIGIT.matcher(votes).replaceAll("");
	}

	private String getRuntimeMinutes(String runtime) {
		List<Integer> n = matchIntegers(runtime);
		switch (n.size()) {
		case 0:
			return null;
		case 1:
			return Integer.toString(n.get(0));// e.g 162 min
		default:
			return Integer.toString(n.get(0) * 60 + n.get(1));// e.g 1h 30min
		}
	}

	private String getReleaseDate(String value) {
		if ("N/A".equals(value)) {
			return null;
		}

		return Stream.of("d MMM yyyy", "yyyy").map(f -> {
			return parsePartialDate(value, f);
		}).filter(Objects::nonNull).map(Objects::toString).findFirst().orElse(null);
	}

	private SimpleDate parsePartialDate(String value, String format) {
		if (value != null && value.length() > 0) {
			try {
				TemporalAccessor f = DateTimeFormatter.ofPattern(format, Locale.ENGLISH).parse(value);
				if (f.isSupported(ChronoField.YEAR)) {
					if (f.isSupported(ChronoField.MONTH_OF_YEAR) && f.isSupported(ChronoField.DAY_OF_MONTH)) {
						return new SimpleDate(f.get(ChronoField.YEAR), f.get(ChronoField.MONTH_OF_YEAR), f.get(ChronoField.DAY_OF_MONTH));
					} else {
						return new SimpleDate(f.get(ChronoField.YEAR), 1, 1);
					}
				}
			} catch (DateTimeParseException e) {
				debug.warning(format("Bad date: %s =~ %s => %s", value, format, e));
			}
		}
		return null;
	}

	private <T> List<T> split(Pattern regex, String value, Function<String, T> toObject) {
		if (value == null || value.isEmpty())
			return emptyList();

		return regex.splitAsStream(value).map(String::trim).filter(s -> !s.isEmpty() && !s.equals("N/A")).map(toObject).collect(toList());
	}

}
