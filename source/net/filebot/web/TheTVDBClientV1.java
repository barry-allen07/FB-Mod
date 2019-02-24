package net.filebot.web;

import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.util.XPathUtilities.*;
import static net.filebot.web.EpisodeUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import net.filebot.Cache;
import net.filebot.Cache.TypedCache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;

public class TheTVDBClientV1 extends AbstractEpisodeListProvider implements ArtworkProvider {

	private static final String DEFAULT_MIRROR = "http://thetvdb.com";

	private static final Map<MirrorType, String> mirrors = MirrorType.newMap();

	private String apikey;

	public TheTVDBClientV1(String apikey) {
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

	/**
	 * Map locale to TheTVDB language code
	 */
	public String getLanguageCode(Locale locale) {
		// Note: ISO 639 is not a stable standard— some languages' codes have changed.
		// Locale's constructor recognizes both the new and the old codes for the languages whose codes have changed,
		// but this function always returns the old code.
		String code = locale.getLanguage();

		// Java language code => TheTVDB language code
		switch (code) {
		case "iw":
			return "he"; // Hebrew
		case "in":
			return "id"; // Indonesian
		}

		return code;
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		// perform online search
		Document dom = getXmlResource(MirrorType.SEARCH, "GetSeries.php?seriesname=" + encode(query, true) + "&language=" + getLanguageCode(locale));

		Map<Integer, SearchResult> resultSet = new LinkedHashMap<Integer, SearchResult>();

		for (Node node : selectNodes("Data/Series", dom)) {
			int sid = matchInteger(getTextContent("seriesid", node));
			String seriesName = getTextContent("SeriesName", node);

			if (seriesName.startsWith("**") && seriesName.endsWith("**")) {
				debug.fine(format("Invalid series: %s [%d]", seriesName, sid));
				continue;
			}

			// collect alias names
			List<String> aliasNames = streamNodes("AliasNames", node).flatMap(it -> {
				return PIPE.splitAsStream(getTextContent(it));
			}).map(String::trim).filter(s -> s.length() > 0).collect(toList());

			if (!resultSet.containsKey(sid)) {
				resultSet.put(sid, new SearchResult(sid, seriesName, aliasNames));
			}
		}

		return new ArrayList<SearchResult>(resultSet.values());
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		Document dom = getXmlResource(MirrorType.XML, "series/" + series.getId() + "/all/" + getLanguageCode(locale) + ".xml");

		// parse series info
		Node seriesNode = selectNode("Data/Series", dom);
		TheTVDBSeriesInfo seriesInfo = new TheTVDBSeriesInfo(this, locale, series.getId());
		seriesInfo.setOrder(sortOrder.name());
		seriesInfo.setAliasNames(series.getAliasNames());

		seriesInfo.setName(getTextContent("SeriesName", seriesNode));
		seriesInfo.setAirsDayOfWeek(getTextContent("Airs_DayOfWeek", seriesNode));
		seriesInfo.setAirsTime(getTextContent("Airs_Time", seriesNode));
		seriesInfo.setCertification(getTextContent("ContentRating", seriesNode));
		seriesInfo.setImdbId(getTextContent("IMDB_ID", seriesNode));
		seriesInfo.setNetwork(getTextContent("Network", seriesNode));
		seriesInfo.setOverview(getTextContent("Overview", seriesNode));
		seriesInfo.setStatus(getTextContent("Status", seriesNode));

		seriesInfo.setRating(getDecimal(getTextContent("Rating", seriesNode)));
		seriesInfo.setRatingCount(matchInteger(getTextContent("RatingCount", seriesNode)));
		seriesInfo.setRuntime(matchInteger(getTextContent("Runtime", seriesNode)));
		seriesInfo.setGenres(getListContent("Genre", "\\|", seriesNode));
		seriesInfo.setStartDate(SimpleDate.parse(getTextContent("FirstAired", seriesNode)));
		seriesInfo.setBannerUrl(getResource(MirrorType.BANNER, getTextContent("banner", seriesNode)));

		// parse episode data
		List<Episode> episodes = new ArrayList<Episode>(50);
		List<Episode> specials = new ArrayList<Episode>(5);

		for (Node node : selectNodes("Data/Episode", dom)) {
			String episodeName = getTextContent("EpisodeName", node);
			Integer absoluteNumber = matchInteger(getTextContent("absolute_number", node));
			SimpleDate airdate = SimpleDate.parse(getTextContent("FirstAired", node));
			Integer id = matchInteger(getTextContent("id", node));

			// default numbering
			Integer episodeNumber = matchInteger(getTextContent("EpisodeNumber", node));
			Integer seasonNumber = matchInteger(getTextContent("SeasonNumber", node));

			// adjust for DVD numbering if possible
			if (sortOrder == SortOrder.DVD) {
				Integer dvdSeasonNumber = matchInteger(getTextContent("DVD_season", node));
				Integer dvdEpisodeNumber = matchInteger(getTextContent("DVD_episodenumber", node));

				// require both values to be valid integer numbers
				if (dvdSeasonNumber != null && dvdEpisodeNumber != null) {
					seasonNumber = dvdSeasonNumber;
					episodeNumber = dvdEpisodeNumber;
				}
			}

			// adjust for special numbering if necessary
			if (seasonNumber == null || seasonNumber == 0) {
				// handle as special episode
				for (String specialSeasonTag : new String[] { "airsafter_season", "airsbefore_season" }) {
					Integer specialSeason = matchInteger(getTextContent(specialSeasonTag, node));
					if (specialSeason != null && specialSeason != 0) {
						seasonNumber = specialSeason;
						break;
					}
				}

				// use given episode number as special number or count specials by ourselves
				Integer specialNumber = (episodeNumber != null) ? episodeNumber : filterBySeason(specials, seasonNumber).size() + 1;
				specials.add(new Episode(seriesInfo.getName(), seasonNumber, null, episodeName, absoluteNumber, specialNumber, airdate, id, new SeriesInfo(seriesInfo)));
			} else {
				// adjust for forced absolute numbering (if possible)
				if (sortOrder == SortOrder.Absolute && absoluteNumber != null && absoluteNumber > 0) {
					seasonNumber = null;
					episodeNumber = absoluteNumber;
				} else if (sortOrder == SortOrder.AbsoluteAirdate && airdate != null) {
					// use airdate as absolute episode number
					seasonNumber = null;
					episodeNumber = airdate.getYear() * 1_00_00 + airdate.getMonth() * 1_00 + airdate.getDay();
				}

				// handle as normal episode
				episodes.add(new Episode(seriesInfo.getName(), seasonNumber, episodeNumber, episodeName, absoluteNumber, null, airdate, id, new SeriesInfo(seriesInfo)));
			}
		}

		// episodes my not be ordered by DVD episode number
		episodes.sort(episodeComparator());

		// add specials at the end
		episodes.addAll(specials);

		return new SeriesData(seriesInfo, episodes);
	}

	public SearchResult lookupByID(int id, Locale locale) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}

		return getLookupCache("id", locale).computeIfAbsent(id, it -> {
			Document dom = getXmlResource(MirrorType.XML, "series/" + id + "/all/" + getLanguageCode(locale) + ".xml");
			String name = selectString("//SeriesName", dom);

			return new SearchResult(id, name);
		});
	}

	public SearchResult lookupByIMDbID(int imdbid, Locale locale) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + imdbid);
		}

		return getLookupCache("imdbid", locale).computeIfAbsent(imdbid, it -> {
			Document dom = getXmlResource(MirrorType.SEARCH, "GetSeriesByRemoteID.php?imdbid=" + imdbid + "&language=" + getLanguageCode(locale));

			String id = selectString("//seriesid", dom);
			String name = selectString("//SeriesName", dom);

			if (id.isEmpty() || name.isEmpty())
				return null;

			return new SearchResult(Integer.parseInt(id), name);
		});
	}

	protected String getMirror(MirrorType mirrorType) throws Exception {
		// use default server
		if (mirrorType == MirrorType.NULL) {
			return DEFAULT_MIRROR;
		}

		synchronized (mirrors) {
			// initialize mirrors
			if (mirrors.isEmpty()) {
				Document dom = getXmlResource(MirrorType.NULL, "mirrors.xml");

				// collect all mirror data
				Map<MirrorType, List<String>> mirrorLists = streamNodes("Mirrors/Mirror", dom).flatMap(node -> {
					String mirror = getTextContent("mirrorpath", node);
					int typeMask = Integer.parseInt(getTextContent("typemask", node));

					return MirrorType.fromTypeMask(typeMask).stream().collect(toMap(m -> m, m -> mirror)).entrySet().stream();
				}).collect(groupingBy(Entry::getKey, MirrorType::newMap, mapping(Entry::getValue, toList())));

				// select random mirror for each type
				Random random = new Random();

				mirrorLists.forEach((type, options) -> {
					String selection = options.get(random.nextInt(options.size()));
					mirrors.put(type, selection);
				});
			}

			// return selected mirror
			return mirrors.getOrDefault(mirrorType, DEFAULT_MIRROR);
		}
	}

	protected Document getXmlResource(MirrorType mirror, String resource) throws Exception {
		Cache cache = Cache.getCache(getName(), CacheType.Monthly);
		return cache.xml(resource, s -> getResource(mirror, s)).get();
	}

	protected URL getResource(MirrorType mirror, String path) throws Exception {
		StringBuilder url = new StringBuilder(getMirror(mirror)).append('/').append(mirror.prefix()).append('/');
		if (mirror.keyRequired()) {
			url.append(apikey).append('/');
		}
		return new URL(url.append(path).toString());
	}

	protected static enum MirrorType {

		NULL(0), SEARCH(1), XML(1), BANNER(2);

		final int bitMask;

		private MirrorType(int bitMask) {
			this.bitMask = bitMask;
		}

		public String prefix() {
			return this != BANNER ? "api" : "banners";
		}

		public boolean keyRequired() {
			return this != BANNER && this != SEARCH;
		}

		public static EnumSet<MirrorType> fromTypeMask(int mask) {
			// convert bit mask to enumset
			return EnumSet.of(SEARCH, XML, BANNER).stream().filter(m -> {
				return (mask & m.bitMask) != 0;
			}).collect(toCollection(MirrorType::newSet));
		};

		public static EnumSet<MirrorType> newSet() {
			return EnumSet.noneOf(MirrorType.class);
		}

		public static <T> EnumMap<MirrorType, T> newMap() {
			return new EnumMap<MirrorType, T>(MirrorType.class);
		}

	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(SearchResult searchResult, Locale language) throws Exception {
		return (TheTVDBSeriesInfo) super.getSeriesInfo(searchResult, language);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	public TheTVDBSeriesInfo getSeriesInfoByIMDbID(int imdbid, Locale locale) throws Exception {
		return getSeriesInfo(lookupByIMDbID(imdbid, locale), locale);
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("http://www.thetvdb.com/?tab=seasonall&id=" + searchResult.getId());
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Document dom = getXmlResource(MirrorType.XML, "series/" + id + "/banners.xml");
		URL mirror = getResource(MirrorType.BANNER, "");

		return streamNodes("//Banner", dom).map(node -> {
			try {
				String type = getTextContent("BannerType", node);
				String subKey = getTextContent("BannerType2", node);
				String fileName = getTextContent("BannerPath", node);
				String season = getTextContent("Season", node);
				String language = getTextContent("Language", node);
				Double rating = getDecimal(getTextContent("Rating", node));

				return new Artwork(Stream.of(type, subKey, season), new URL(mirror, fileName), language == null ? null : new Locale(language), rating);
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::getMessage);
				return null;
			}
		}).filter(Objects::nonNull).filter(it -> it.getTags().contains(category)).collect(toList());
	}

	protected TypedCache<SearchResult> getLookupCache(String type, Locale language) {
		// lookup should always yield the same results so we can cache it for longer
		return Cache.getCache(getName() + "_" + "lookup" + "_" + type + "_" + language, CacheType.Monthly).cast(SearchResult.class);
	}

}
