package net.filebot.web;

import static net.filebot.WebServices.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class TheTVDBClientTest {

	SearchResult buffy = new SearchResult(70327, "Buffy the Vampire Slayer");
	SearchResult wonderfalls = new SearchResult(78845, "Wonderfalls");
	SearchResult firefly = new SearchResult(78874, "Firefly");

	@Test
	public void search() throws Exception {
		// test default language and query escaping (blanks)
		List<SearchResult> results = TheTVDB.search("babylon 5", Locale.ENGLISH);

		assertEquals(2, results.size());

		SearchResult first = results.get(0);

		assertEquals("Babylon 5", first.getName());
		assertEquals(70726, first.getId());
	}

	@Test
	public void searchGerman() throws Exception {
		List<SearchResult> results = TheTVDB.search("Buffy", Locale.GERMAN);

		SearchResult first = results.get(0);
		assertEquals("Buffy", first.getName());
		assertEquals(70327, first.getId());
	}

	@Test
	public void getEpisodeListAll() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(buffy, SortOrder.Airdate, Locale.ENGLISH);

		assertEquals(145, list.size());

		// check ordinary episode
		Episode first = list.get(0);
		assertEquals("Buffy the Vampire Slayer", first.getSeriesName());
		assertEquals("1997-03-10", first.getSeriesInfo().getStartDate().toString());
		assertEquals("Welcome to the Hellmouth (1)", first.getTitle());
		assertEquals("1", first.getEpisode().toString());
		assertEquals("1", first.getSeason().toString());
		assertEquals("1", first.getAbsolute().toString());
		assertEquals("1997-03-10", first.getAirdate().toString());

		// check special episode
		Episode last = list.get(list.size() - 1);
		assertEquals("Buffy the Vampire Slayer", last.getSeriesName());
		assertEquals("Unaired Pilot", last.getTitle());
		assertEquals(null, last.getSeason());
		assertEquals(null, last.getEpisode());
		assertEquals(null, last.getAbsolute());
		assertEquals("1", last.getSpecial().toString());
		assertEquals(null, last.getAirdate());
	}

	@Test
	public void getEpisodeListSingleSeason() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(wonderfalls, SortOrder.Airdate, Locale.ENGLISH);

		Episode first = list.get(0);

		assertEquals("Wonderfalls", first.getSeriesName());
		assertEquals("2004-03-12", first.getSeriesInfo().getStartDate().toString());
		assertEquals("Wax Lion", first.getTitle());
		assertEquals("1", first.getEpisode().toString());
		assertEquals("1", first.getSeason().toString());
		assertEquals(null, first.getAbsolute()); // should be "1" but data has not yet been entered
		assertEquals("2004-03-12", first.getAirdate().toString());
		assertEquals("296337", first.getId().toString());
	}

	@Test
	public void getEpisodeListMissingInformation() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(wonderfalls, SortOrder.Airdate, Locale.JAPANESE);

		Episode first = list.get(0);

		assertEquals("Wonderfalls", first.getSeriesName());
		assertEquals("Wax Lion", first.getTitle());
	}

	@Test
	public void getEpisodeListIllegalSeries() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(new SearchResult(313193, "*** DOES NOT EXIST ***"), SortOrder.Airdate, Locale.ENGLISH);
		assertTrue(list.isEmpty());
	}

	@Test
	public void getEpisodeListNumberingDVD() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(firefly, SortOrder.DVD, Locale.ENGLISH);

		Episode first = list.get(0);
		assertEquals("Firefly", first.getSeriesName());
		assertEquals("2002-09-20", first.getSeriesInfo().getStartDate().toString());
		assertEquals("Serenity", first.getTitle());
		assertEquals("1", first.getEpisode().toString());
		assertEquals("1", first.getSeason().toString());
		assertEquals("1", first.getAbsolute().toString());
		assertEquals("2002-12-20", first.getAirdate().toString());
	}

	@Test
	public void getEpisodeListNumberingAbsoluteAirdate() throws Exception {
		List<Episode> list = TheTVDB.getEpisodeList(firefly, SortOrder.AbsoluteAirdate, Locale.ENGLISH);

		Episode first = list.get(0);
		assertEquals("Firefly", first.getSeriesName());
		assertEquals("2002-09-20", first.getSeriesInfo().getStartDate().toString());
		assertEquals("The Train Job", first.getTitle());
		assertEquals("20020920", first.getEpisode().toString());
		assertEquals(null, first.getSeason());
		assertEquals("2", first.getAbsolute().toString());
		assertEquals("2002-09-20", first.getAirdate().toString());
	}

	public void getEpisodeListLink() {
		assertEquals("http://www.thetvdb.com/?tab=seasonall&id=78874", TheTVDB.getEpisodeListLink(firefly).toString());
	}

	@Test
	public void lookupByID() throws Exception {
		SearchResult series = TheTVDB.lookupByID(78874, Locale.ENGLISH);
		assertEquals("Firefly", series.getName());
		assertEquals(78874, series.getId());
	}

	@Test
	public void lookupByIMDbID() throws Exception {
		SearchResult series = TheTVDB.lookupByIMDbID(303461, Locale.ENGLISH);
		assertEquals("Firefly", series.getName());
		assertEquals(78874, series.getId());
	}

	@Test
	public void getSeriesInfo() throws Exception {
		TheTVDBSeriesInfo it = TheTVDB.getSeriesInfo(80348, Locale.ENGLISH);

		assertEquals(80348, it.getId(), 0);
		assertEquals("Action", it.getGenres().get(0));
		assertEquals("en", it.getLanguage());
		assertEquals("45", it.getRuntime().toString());
		assertEquals("Chuck", it.getName());
		assertEquals(9.0, it.getRating(), 0.5);
		assertEquals(1000, it.getRatingCount(), 100);
		assertEquals("tt0934814", it.getImdbId());
		assertEquals("Friday", it.getAirsDayOfWeek());
		assertEquals("8:00 PM", it.getAirsTime());
		assertEquals(500, it.getOverview().length(), 100);
		assertEquals("http://thetvdb.com/banners/graphical/80348-g26.jpg", it.getBannerUrl().toString());
	}

	@Test
	public void getArtwork() throws Exception {
		Artwork i = TheTVDB.getArtwork(buffy.getId(), "fanart", Locale.ENGLISH).get(0);

		assertEquals("[fanart, graphical, 1280x720]", i.getTags().toString());
		assertEquals("http://thetvdb.com/banners/fanart/original/70327-31.jpg", i.getUrl().toString());
		assertTrue(i.matches("fanart", "1280x720"));
		assertFalse(i.matches("fanart", "1280x720", "1"));
		assertEquals(8.0, i.getRating(), 1.0);
	}

	@Test
	public void getLanguages() throws Exception {
		List<String> languages = TheTVDB.getLanguages();
		assertEquals("[zh, en, sv, no, da, fi, nl, de, it, es, fr, pl, hu, el, tr, ru, he, ja, pt, cs, sl, hr, ko]", languages.toString());
	}

	@Test
	public void getActors() throws Exception {
		Person p = TheTVDB.getActors(firefly.getId(), Locale.ENGLISH).get(0);
		assertEquals("Alan Tudyk", p.getName());
		assertEquals("Hoban 'Wash' Washburne", p.getCharacter());
		assertEquals("Actor", p.getJob());
		assertEquals(null, p.getDepartment());
		assertEquals("0", p.getOrder().toString());
		assertEquals("http://thetvdb.com/banners/actors/68409.jpg", p.getImage().toString());
	}

	@Test
	public void getEpisodeInfo() throws Exception {
		EpisodeInfo i = TheTVDB.getEpisodeInfo(296337, Locale.ENGLISH);

		assertEquals("78845", i.getSeriesId().toString());
		assertEquals("296337", i.getId().toString());
		assertEquals(8.2, i.getRating(), 0.1);
		assertEquals(6, i.getVotes(), 5);
		assertEquals("When Jaye Tyler is convinced by a waxed lion to chase after a shinny quarter, she finds herself returning a lost purse to a lady (who instead of thanking her, is punched in the face), meeting an attractive and sweet bartender names Eric, introducing her sister, Sharon to the EPS newly divorced bachelor, Thomas, she knows, and later discovering her sister, Sharon's sexuality.", i.getOverview().toString());
		assertEquals("[Todd Holland, Bryan Fuller, Todd Holland]", i.getDirectors().toString());
		assertEquals("[Todd Holland, Bryan Fuller]", i.getWriters().toString());
		assertEquals("[Scotch Ellis Loring, Gerry Fiorini, Kim Roberts, Corry Karpf, Curt Wu, Bailey Stocker, Lisa Marcos, Jorge Molina, Morgan Drmaj, Chantal Purdy, Kari Matchett, Neil Grayston, Anna Starnino, Melissa Grelo, Brandon Oakes, Scotch Ellis Loring, Ted Dykstra, Kathryn Greenwood, G]", i.getActors().toString());
	}

}
