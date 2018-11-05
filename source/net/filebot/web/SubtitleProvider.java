package net.filebot.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public interface SubtitleProvider extends Datasource {

	public List<SubtitleSearchResult> search(String query) throws Exception;

	public List<SubtitleSearchResult> guess(String tag) throws Exception;

	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int[][] episodeFilter, Locale locale) throws Exception;

	public URI getSubtitleListLink(SubtitleSearchResult searchResult, Locale locale);

	public URI getLink();

}
