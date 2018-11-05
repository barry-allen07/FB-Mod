package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;

public class TVMazeClient extends AbstractEpisodeListProvider {

	@Override
	public String getIdentifier() {
		return "TVmaze";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.tvmaze");
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
	protected Locale vetoRequestParameter(Locale language) {
		return Locale.ENGLISH;
	}

	@Override
	public List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		// e.g. http://api.tvmaze.com/search/shows?q=girls
		Object response = request("search/shows?q=" + encode(query, true));

		// FUTURE WORK: consider adding TVmaze aka titles for each result, e.g. http://api.tvmaze.com/shows/1/akas
		return streamJsonObjects(response).map(it -> {
			Object show = it.get("show");
			Integer id = getInteger(show, "id");
			String name = getString(show, "name");

			return new SearchResult(id, name);
		}).collect(toList());
	}

	protected SeriesInfo fetchSeriesInfo(SearchResult show, SortOrder sortOrder, Locale locale) throws Exception {
		// e.g. http://api.tvmaze.com/shows/1
		Object response = request("shows/" + show.getId());

		String status = getStringValue(response, "status", String::new);
		SimpleDate premiered = getStringValue(response, "premiered", SimpleDate::parse);
		Integer runtime = getStringValue(response, "runtime", Integer::parseInt);
		Object[] genres = getArray(response, "genres");
		Double rating = getStringValue(getMap(response, "rating"), "average", Double::parseDouble);

		SeriesInfo seriesInfo = new SeriesInfo(this, sortOrder, locale, show.getId());
		seriesInfo.setName(show.getName());
		seriesInfo.setAliasNames(show.getAliasNames());
		seriesInfo.setStatus(status);
		seriesInfo.setRuntime(runtime);
		seriesInfo.setStartDate(premiered);
		seriesInfo.setRating(rating);
		seriesInfo.setGenres(stream(genres).map(Objects::toString).collect(toList()));

		return seriesInfo;
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult searchResult, SortOrder sortOrder, Locale locale) throws Exception {
		SeriesInfo seriesInfo = fetchSeriesInfo(searchResult, sortOrder, locale);

		// e.g. http://api.tvmaze.com/shows/1/episodes
		Object response = request("shows/" + seriesInfo.getId() + "/episodes");

		List<Episode> episodes = streamJsonObjects(response).map(episode -> {
			Integer id = getInteger(episode, "id");
			Integer seasonNumber = getInteger(episode, "season");
			Integer episodeNumber = getInteger(episode, "number");
			String episodeTitle = getString(episode, "name");
			SimpleDate airdate = getStringValue(episode, "airdate", SimpleDate::parse);

			return new Episode(seriesInfo.getName(), seasonNumber, episodeNumber, episodeTitle, null, null, airdate, id, seriesInfo);
		}).collect(toList());

		return new SeriesData(seriesInfo, episodes);
	}

	protected Object request(String resource) throws Exception {
		Cache cache = Cache.getCache(getName(), CacheType.Monthly);
		return cache.json(resource, s -> getResource(resource)).get();
	}

	protected URL getResource(String resource) throws Exception {
		return new URL("http://api.tvmaze.com/" + resource);
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("http://www.tvmaze.com/shows/" + searchResult.getId());
	}

}
