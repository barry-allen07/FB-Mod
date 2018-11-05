package net.filebot.web;

import static java.util.Arrays.*;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import net.filebot.Cache;
import net.filebot.Cache.TypedCache;
import net.filebot.CacheType;

public abstract class AbstractEpisodeListProvider implements EpisodeListProvider {

	protected abstract List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception;

	protected abstract SeriesData fetchSeriesData(SearchResult searchResult, SortOrder sortOrder, Locale locale) throws Exception;

	@Override
	public List<SearchResult> search(String query, Locale language) throws Exception {
		return getSearchCache(language).computeIfAbsent(query, it -> {
			return fetchSearchResult(query, language);
		});
	}

	protected SortOrder vetoRequestParameter(SortOrder order) {
		return order == null ? SortOrder.Airdate : order;
	}

	protected Locale vetoRequestParameter(Locale language) {
		return language == null || language.getLanguage().isEmpty() ? Locale.ENGLISH : language;
	}

	@Override
	public List<Episode> getEpisodeList(SearchResult searchResult, SortOrder sortOrder, Locale language) throws Exception {
		return getSeriesData(searchResult, sortOrder, language).getEpisodeList();
	}

	@Override
	public List<Episode> getEpisodeList(int id, SortOrder order, Locale language) throws Exception {
		return getEpisodeList(new SearchResult(id), order, language);
	}

	@Override
	public SeriesInfo getSeriesInfo(SearchResult searchResult, Locale language) throws Exception {
		return getSeriesData(searchResult, null, language).getSeriesInfo();
	}

	@Override
	public SeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	protected SeriesData getSeriesData(SearchResult searchResult, SortOrder order, Locale language) throws Exception {
		// override preferences if requested parameters are not supported
		SortOrder requestOrder = vetoRequestParameter(order);
		Locale requestLanguage = vetoRequestParameter(language);

		return getDataCache(requestOrder, requestLanguage).computeIfAbsent(searchResult.getId(), it -> {
			return fetchSeriesData(searchResult, requestOrder, requestLanguage);
		});
	}

	protected Cache getCache(String section) {
		return Cache.getCache(getName() + "_" + section, CacheType.Daily);
	}

	protected TypedCache<List<SearchResult>> getSearchCache(Locale language) {
		return getCache("search_" + language).castList(SearchResult.class);
	}

	protected TypedCache<SeriesData> getDataCache(SortOrder order, Locale language) {
		return getCache("data_" + order.ordinal() + "_" + language).cast(SeriesData.class);
	}

	protected static class SeriesData implements Serializable {

		public SeriesInfo seriesInfo;
		public Episode[] episodeList;

		public SeriesData() {
			// used by serializer
		}

		public SeriesData(SeriesInfo seriesInfo, List<Episode> episodeList) {
			this.seriesInfo = seriesInfo;
			this.episodeList = episodeList.toArray(new Episode[episodeList.size()]);
		}

		public SeriesInfo getSeriesInfo() {
			return seriesInfo.clone();
		}

		public List<Episode> getEpisodeList() {
			return asList(episodeList.clone());
		}

	}

}
