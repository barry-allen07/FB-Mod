package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.JsonUtilities.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import javax.swing.Icon;

public class TMDbTVClient extends AbstractEpisodeListProvider {

	private final TMDbClient tmdb;

	public TMDbTVClient(TMDbClient tmdb) {
		this.tmdb = tmdb;
	}

	@Override
	public String getIdentifier() {
		return "TheMovieDB::TV";
	}

	@Override
	public String getName() {
		return tmdb.getName();
	}

	@Override
	public Icon getIcon() {
		return tmdb.getIcon();
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	@Override
	protected SortOrder vetoRequestParameter(SortOrder order) {
		return SortOrder.Airdate;
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://www.themoviedb.org/tv/" + searchResult.getId());
	}

	@Override
	protected List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		// query by name with year filter if possible
		Matcher nameYear = tmdb.getNameYearMatcher(query);
		if (nameYear.matches()) {
			return searchTV(nameYear.group(1).trim(), Integer.parseInt(nameYear.group(2)), locale, true);
		} else {
			return searchTV(query.trim(), -1, locale, true);
		}
	}

	public List<SearchResult> searchTV(String seriesName, int startYear, Locale locale, boolean extendedInfo) throws Exception {
		Map<String, Object> query = new LinkedHashMap<String, Object>(2);
		query.put("query", seriesName);
		if (startYear > 0) {
			query.put("first_air_date_year", startYear);
		}
		Object response = tmdb.request("search/tv", query, locale);

		return streamJsonObjects(response, "results").map(it -> {
			Integer id = getInteger(it, "id");
			String name = getString(it, "name");
			String originalName = getString(it, "original_name");

			if (name == null) {
				name = originalName;
			}

			if (id == null || name == null) {
				return null;
			}

			String[] alternativeTitles = tmdb.getAlternativeTitles("tv/" + id, "results", name, originalName, extendedInfo);

			return new SearchResult(id, name, alternativeTitles);
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		// http://api.themoviedb.org/3/tv/id
		Object tv = tmdb.request("tv/" + series.getId(), emptyMap(), locale);

		// retrieve localized series name from response
		String name = getString(tv, "name");
		String originalName = getString(tv, "original_name");

		SeriesInfo info = new SeriesInfo(this, sortOrder, locale, series.getId());
		info.setName(name);
		info.setAliasNames(Stream.concat(Stream.of(series.getName(), originalName), Stream.of(series.getAliasNames())).filter(Objects::nonNull).filter(s -> !s.equals(name)).distinct().toArray(String[]::new));
		info.setStatus(getString(tv, "status"));
		info.setLanguage(getString(tv, "original_language"));
		info.setStartDate(getStringValue(tv, "first_air_date", SimpleDate::parse));
		info.setRating(getStringValue(tv, "vote_average", Double::parseDouble));
		info.setRatingCount(getStringValue(tv, "vote_count", Integer::parseInt));
		info.setRuntime(stream(getArray(tv, "episode_run_time")).map(Object::toString).map(Integer::parseInt).findFirst().orElse(null));
		info.setGenres(streamJsonObjects(tv, "genres").map(it -> getString(it, "name")).collect(toList()));
		info.setNetwork(streamJsonObjects(tv, "networks").map(it -> getString(it, "name")).findFirst().orElse(null));

		int[] seasons = streamJsonObjects(tv, "seasons").mapToInt(it -> getInteger(it, "season_number")).toArray();
		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		for (int s : seasons) {
			// http://api.themoviedb.org/3/tv/id/season/season_number
			Object season = tmdb.request("tv/" + series.getId() + "/season/" + s, emptyMap(), locale);

			streamJsonObjects(season, "episodes").forEach(episode -> {
				Integer id = getInteger(episode, "id");
				Integer episodeNumber = getInteger(episode, "episode_number");
				Integer seasonNumber = getInteger(episode, "season_number");
				String episodeTitle = getString(episode, "name");
				SimpleDate airdate = getStringValue(episode, "air_date", SimpleDate::parse);

				Integer absoluteNumber = episodes.size() + 1;

				if (s > 0) {
					episodes.add(new Episode(name, seasonNumber, episodeNumber, episodeTitle, absoluteNumber, null, airdate, id, info));
				} else {
					specials.add(new Episode(name, null, null, episodeTitle, null, episodeNumber, airdate, id, info));
				}
			});
		}

		// add specials at the end
		episodes.addAll(specials);

		return new SeriesData(info, episodes);
	}

}
