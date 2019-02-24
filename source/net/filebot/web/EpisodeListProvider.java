package net.filebot.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public interface EpisodeListProvider extends Datasource {

	public boolean hasSeasonSupport();

	public List<SearchResult> search(String query, Locale locale) throws Exception;

	public List<Episode> getEpisodeList(SearchResult searchResult, SortOrder order, Locale locale) throws Exception;

	public List<Episode> getEpisodeList(int id, SortOrder order, Locale locale) throws Exception;

	public SeriesInfo getSeriesInfo(SearchResult searchResult, Locale locale) throws Exception;

	public SeriesInfo getSeriesInfo(int id, Locale locale) throws Exception;

	public URI getEpisodeListLink(SearchResult searchResult);

}
