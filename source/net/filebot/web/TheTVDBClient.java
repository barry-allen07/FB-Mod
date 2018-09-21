package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.CachedResource.fetchIfModified;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.web.EpisodeUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;

public class TheTVDBClient extends AbstractEpisodeListProvider implements ArtworkProvider {

	private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

	private String apikey;

	public TheTVDBClient(String apikey) {
		this.apikey = apikey;
	}

	@Override
	public String getIdentifier() {
		return "TheTVDB";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.thetvdb");
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	protected Object postJson(String path, Object object) throws Exception {
		// curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' 'https://api.thetvdb.com/login' --data '{"apikey":"XXXXX"}'
		ByteBuffer response = post(getEndpoint(path), json(object, false).getBytes(UTF_8), "application/json", null);
		return readJson(UTF_8.decode(response));
	}

	protected Object requestJson(String path, Locale locale, Duration expirationTime) throws Exception {
		Cache cache = Cache.getCache(locale == null || locale == Locale.ROOT ? getName() : getName() + "_" + locale.getLanguage(), CacheType.Monthly);
		return cache.json(path, this::getEndpoint).fetch(fetchIfModified(() -> getRequestHeader(locale))).expire(expirationTime).get();
	}

	protected URL getEndpoint(String path) throws Exception {
		return new URL("https://api.thetvdb.com/" + path);
	}

	private Map<String, String> getRequestHeader(Locale locale) {
		Map<String, String> header = new LinkedHashMap<String, String>(3);

		getLanguageCode(locale).ifPresent(languageCode -> {
			header.put("Accept-Language", languageCode);
		});

		header.put("Accept", "application/json");
		header.put("Authorization", "Bearer " + getAuthorizationToken());

		return header;
	}

	private Optional<String> getLanguageCode(Locale locale) {
		// Note: ISO 639 is not a stable standard— some languages' codes have changed.
		// Locale's constructor recognizes both the new and the old codes for the languages whose codes have changed,
		// but this function always returns the old code.
		return Optional.ofNullable(locale).map(Locale::getLanguage).map(code -> {
			switch (code) {
			case "iw":
				return "he"; // Hebrew
			case "in":
				return "id"; // Indonesian
			case "":
				return null; // empty language code
			default:
				return code;
			}
		});
	}

	private String token = null;
	private Instant tokenExpireInstant = null;
	private Duration tokenExpireDuration = Duration.ofHours(23); // token expires after 24 hours

	private String getAuthorizationToken() {
		synchronized (tokenExpireDuration) {
			if (token == null || (tokenExpireInstant != null && Instant.now().isAfter(tokenExpireInstant))) {
				try {
					Object json = postJson("login", singletonMap("apikey", apikey));
					token = getString(json, "token");
					tokenExpireInstant = Instant.now().plus(tokenExpireDuration);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to retrieve authorization token: " + e.getMessage(), e);
				}
			}
			return token;
		}
	}

	protected List<SearchResult> search(String path, Map<String, Object> query, Locale locale, Duration expirationTime) throws Exception {
		Object json = requestJson(path + "?" + encodeParameters(query, true), locale, expirationTime);

		return streamJsonObjects(json, "data").map(it -> {
			// e.g. aliases, banner, firstAired, id, network, overview, seriesName, status
			int id = getInteger(it, "id");
			String seriesName = getString(it, "seriesName");
			String[] aliasNames = stream(getArray(it, "aliases")).toArray(String[]::new);

			if (seriesName == null || seriesName.startsWith("**") || seriesName.endsWith("**")) {
				debug.warning(format("Ignore invalid series: %s [%d]", seriesName, id));
				return null;
			}

			return new SearchResult(id, seriesName, aliasNames);
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		return search("search/series", singletonMap("name", query), locale, Cache.ONE_DAY);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
		Object json = requestJson("series/" + series.getId(), locale, Cache.ONE_WEEK);
		Object data = getMap(json, "data");

		TheTVDBSeriesInfo info = new TheTVDBSeriesInfo(this, locale, series.getId());
		info.setSlug(getString(data, "slug"));
		info.setAliasNames(Stream.of(series.getAliasNames(), getArray(data, "aliases")).flatMap(it -> stream(it)).map(Object::toString).distinct().toArray(String[]::new));

		info.setName(getString(data, "seriesName"));
		info.setCertification(getString(data, "rating"));
		info.setNetwork(getString(data, "network"));
		info.setStatus(getString(data, "status"));

		info.setRating(getDouble(data, "siteRating"));
		info.setRatingCount(getInteger(data, "siteRatingCount"));

		info.setRuntime(matchInteger(getString(data, "runtime")));
		info.setGenres(stream(getArray(data, "genre")).map(Object::toString).collect(toList()));
		info.setStartDate(getStringValue(data, "firstAired", SimpleDate::parse));

		// TheTVDB SeriesInfo extras
		info.setImdbId(getString(data, "imdbId"));
		info.setOverview(getString(data, "overview"));
		info.setAirsDayOfWeek(getString(data, "airsDayOfWeek"));
		info.setAirsTime(getString(data, "airsTime"));
		info.setBannerUrl(getStringValue(data, "banner", this::resolveImage));
		info.setLastUpdated(getStringValue(data, "lastUpdated", Long::parseLong));

		return info;
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		// fetch series info
		SeriesInfo info = getSeriesInfo(series, locale);
		info.setOrder(sortOrder.name());

		// ignore preferred language if basic series information isn't even available
		if (info.getName() == null && !locale.equals(DEFAULT_LOCALE)) {
			return fetchSeriesData(series, sortOrder, DEFAULT_LOCALE);
		}

		// fetch episode data
		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		for (int i = 1, n = 1; i <= n; i++) {
			Object json = requestJson("series/" + series.getId() + "/episodes?page=" + i, locale, Cache.ONE_DAY);

			Integer lastPage = getInteger(getMap(json, "links"), "last");
			if (lastPage != null) {
				n = lastPage;
			}

			streamJsonObjects(json, "data").forEach(it -> {
				Integer id = getInteger(it, "id");
				String episodeName = getString(it, "episodeName");

				// default to English episode title if the preferred language is not available
				if (episodeName == null && !locale.equals(DEFAULT_LOCALE)) {
					try {
						episodeName = getEpisodeList(series, sortOrder, DEFAULT_LOCALE).stream().filter(e -> id.equals(e.getId())).findFirst().map(Episode::getTitle).orElse(null);
					} catch (Exception e) {
						debug.warning(cause("Failed to retrieve default episode title", e));
					}
				}

				Integer absoluteNumber = getInteger(it, "absoluteNumber");
				SimpleDate airdate = getStringValue(it, "firstAired", SimpleDate::parse);

				// default numbering
				Integer episodeNumber = getInteger(it, "airedEpisodeNumber");
				Integer seasonNumber = getInteger(it, "airedSeason");

				// adjust for forced absolute numbering (if possible)
				if (sortOrder == SortOrder.DVD) {
					Integer dvdSeasonNumber = getInteger(it, "dvdSeason");
					Number dvdEpisodeNumber = getDecimal(it, "dvdEpisodeNumber"); // e.g. 4.2

					// require both values to be valid integer numbers
					if (dvdSeasonNumber != null && dvdEpisodeNumber != null) {
						seasonNumber = dvdSeasonNumber;
						episodeNumber = dvdEpisodeNumber.intValue();

						if (episodeNumber.doubleValue() != dvdEpisodeNumber.doubleValue()) {
							debug.finest(format("[%s] Coerce episode number [%s] to [%s]", info, dvdEpisodeNumber, episodeNumber));
						}
					}
				} else if (sortOrder == SortOrder.Absolute && absoluteNumber != null && absoluteNumber > 0) {
					seasonNumber = null;
					episodeNumber = absoluteNumber;
				} else if (sortOrder == SortOrder.AbsoluteAirdate && airdate != null) {
					// use airdate as absolute episode number
					seasonNumber = null;
					episodeNumber = airdate.getYear() * 1_00_00 + airdate.getMonth() * 1_00 + airdate.getDay();
				}

				if (seasonNumber == null || seasonNumber > 0) {
					// handle as normal episode
					episodes.add(new Episode(info.getName(), seasonNumber, episodeNumber, episodeName, absoluteNumber, null, airdate, id, new SeriesInfo(info)));
				} else {
					// handle as special episode
					specials.add(new Episode(info.getName(), null, null, episodeName, absoluteNumber, episodeNumber, airdate, id, new SeriesInfo(info)));
				}
			});
		}

		// episodes my not be ordered by DVD episode number
		episodes.sort(episodeComparator());

		// add specials at the end
		episodes.addAll(specials);

		return new SeriesData(info, episodes);
	}

	public SearchResult lookupByID(int id, Locale locale) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}

		SeriesInfo info = getSeriesInfo(new SearchResult(id), locale);
		return new SearchResult(id, info.getName(), info.getAliasNames());
	}

	public SearchResult lookupByIMDbID(int imdbid, Locale locale) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + imdbid);
		}

		List<SearchResult> result = search("search/series", singletonMap("imdbId", String.format("tt%07d", imdbid)), locale, Cache.ONE_MONTH);
		return result.size() > 0 ? result.get(0) : null;
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://www.thetvdb.com/?tab=seasonall&id=" + searchResult.getId());
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Object json = requestJson("series/" + id + "/images/query?keyType=" + category, locale, Cache.ONE_MONTH);

		return streamJsonObjects(json, "data").map(it -> {
			String subKey = getString(it, "subKey");
			String resolution = getString(it, "resolution");
			URL url = getStringValue(it, "fileName", this::resolveImage);
			Double rating = getDouble(getMap(it, "ratingsInfo"), "average");

			return new Artwork(Stream.of(category, subKey, resolution), url, locale, rating);
		}).sorted(Artwork.RATING_ORDER).collect(toList());
	}

	protected URL resolveImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		// TheTVDB API v2 does not have a dedicated banner mirror
		try {
			return new URL("https://thetvdb.com/banners/" + path);
		} catch (Exception e) {
			throw new IllegalArgumentException(path, e);
		}
	}

	public List<String> getLanguages() throws Exception {
		Object response = requestJson("languages", Locale.ROOT, Cache.ONE_MONTH);
		return streamJsonObjects(response, "data").map(it -> getString(it, "abbreviation")).collect(toList());
	}

	public List<Person> getActors(int seriesId, Locale locale) throws Exception {
		Object response = requestJson("series/" + seriesId + "/actors", locale, Cache.ONE_MONTH);

		// e.g. [id:68414, seriesId:78874, name:Summer Glau, role:River Tam, sortOrder:2, image:actors/68414.jpg, imageAuthor:513, imageAdded:0000-00-00 00:00:00, lastUpdated:2011-08-18 11:53:14]
		return streamJsonObjects(response, "data").map(it -> {
			String name = getString(it, "name");
			String character = getString(it, "role");
			Integer order = getInteger(it, "sortOrder");
			URL image = getStringValue(it, "image", this::resolveImage);

			return new Person(name, character, Person.ACTOR, null, order, image);
		}).sorted(Person.CREDIT_ORDER).collect(toList());
	}

	public EpisodeInfo getEpisodeInfo(int id, Locale locale) throws Exception {
		Object response = requestJson("episodes/" + id, locale, Cache.ONE_MONTH);
		Object data = getMap(response, "data");

		Integer seriesId = getInteger(data, "seriesId");
		String overview = getString(data, "overview");

		Double rating = getDouble(data, "siteRating");
		Integer votes = getInteger(data, "siteRatingCount");

		List<Person> people = new ArrayList<Person>();

		for (Object it : getArray(data, "directors")) {
			people.add(new Person(it.toString(), Person.DIRECTOR));
		}
		for (Object it : getArray(data, "writers")) {
			people.add(new Person(it.toString(), Person.WRITER));
		}
		for (Object it : getArray(data, "guestStars")) {
			people.add(new Person(it.toString(), Person.GUEST_STAR));
		}

		return new EpisodeInfo(this, locale, seriesId, id, people, overview, rating, votes);
	}

}
