package net.filebot.web;

import static java.util.Collections.*;
import static net.filebot.Settings.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import net.filebot.web.OpenSubtitlesSubtitleDescriptor.Property;
import net.filebot.web.OpenSubtitlesXmlRpc.Query;
import net.filebot.web.OpenSubtitlesXmlRpc.SubFile;
import net.filebot.web.OpenSubtitlesXmlRpc.TryUploadResponse;

public class OpenSubtitlesXmlRpcTest {

	private static OpenSubtitlesXmlRpc xmlrpc = new OpenSubtitlesXmlRpc(String.format("%s %s", getApiKey("opensubtitles"), getApplicationVersion()));

	@BeforeClass
	public static void login() throws Exception {
		// login manually
		xmlrpc.loginAnonymous();
	}

	@Test
	public void search() throws Exception {
		List<SubtitleSearchResult> list = xmlrpc.searchMoviesOnIMDB("babylon 5");
		Movie sample = list.get(0);

		// check sample entry
		assertEquals("Babylon 5", sample.getName());
		assertEquals(1994, sample.getYear());
		assertEquals(105946, sample.getImdbId());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void searchOST() throws Exception {
		List<SubtitleSearchResult> list = xmlrpc.searchMoviesOnIMDB("Linkin.Park.New.Divide.1280-720p.Transformers.Revenge.of.the.Fallen.ost");

		// seek to OST entry, expect to fail
		for (int i = 0; !list.get(i).getName().contains("Linkin.Park"); i++)
			;
	}

	@Test
	public void getSubtitleListEnglish() throws Exception {
		List<OpenSubtitlesSubtitleDescriptor> list = xmlrpc.searchSubtitles(singleton(Query.forImdbId(361256, -1, -1, "eng")));

		SubtitleDescriptor sample = list.get(0);

		assertTrue(sample.getName().startsWith("Wonderfalls"));
		assertEquals("English", sample.getLanguageName());

		// check size
		assertTrue(list.size() > 20);
	}

	@Test
	public void getSubtitleListMovieHash() throws Exception {
		List<OpenSubtitlesSubtitleDescriptor> list = xmlrpc.searchSubtitles(singleton(Query.forHash("2bba5c34b007153b", 717565952, "eng")));

		OpenSubtitlesSubtitleDescriptor sample = list.get(0);

		assertEquals("firefly.s01e01.serenity.pilot.dvdrip.xvid.srt", sample.getProperty(Property.SubFileName));
		assertEquals("English", sample.getProperty(Property.LanguageName));
		assertEquals("moviehash", sample.getProperty(Property.MatchedBy));
	}

	@Test
	public void tryUploadSubtitles() throws Exception {
		SubFile subtitle = new SubFile();
		subtitle.setSubFileName("firefly.s01e01.serenity.pilot.dvdrip.xvid.srt");
		subtitle.setSubHash("6d9c600fb8b07f87ffcf156e4ed308ca");
		subtitle.setMovieFileName("firefly.s01e01.serenity.pilot.dvdrip.xvid.avi");
		subtitle.setMovieHash("2bba5c34b007153b");
		subtitle.setMovieByteSize(717565952);

		TryUploadResponse response = xmlrpc.tryUploadSubtitles(subtitle);

		assertFalse(response.isUploadRequired());
		assertEquals("4513264", response.getSubtitleData().get(0).get(Property.IDSubtitle.toString()));
		assertEquals("eng", response.getSubtitleData().get(0).get(Property.SubLanguageID.toString()));
	}

	@Test
	public void checkSubHash() throws Exception {
		Map<String, Integer> subHashMap = xmlrpc.checkSubHash(singleton("e12715f466ee73c86694b7ab9f311285"));

		assertEquals("247060", subHashMap.values().iterator().next().toString());
		assertTrue(1 == subHashMap.size());
	}

	@Test
	public void checkSubHashInvalid() throws Exception {
		Map<String, Integer> subHashMap = xmlrpc.checkSubHash(singleton("0123456789abcdef0123456789abcdef"));

		assertEquals("0", subHashMap.values().iterator().next().toString());
		assertTrue(1 == subHashMap.size());
	}

	@Test
	public void checkMovieHash() throws Exception {
		Map<String, Movie> results = xmlrpc.checkMovieHash(singleton("d7aa0275cace4410"), 0);
		Movie movie = results.get("d7aa0275cace4410");

		assertEquals("Iron Man", movie.getName());
		assertEquals(2008, movie.getYear());
		assertEquals(371746, movie.getImdbId());
	}

	@Test
	public void checkMovieHashInvalid() throws Exception {
		Map<String, Movie> results = xmlrpc.checkMovieHash(singleton("0123456789abcdef"), 0);

		// no movie info
		assertTrue(results.isEmpty());
	}

	@Test
	public void getIMDBMovieDetails() throws Exception {
		Movie movie = xmlrpc.getIMDBMovieDetails(371746);

		assertEquals("Iron Man", movie.getName());
		assertEquals(2008, movie.getYear());
		assertEquals(371746, movie.getImdbId());
	}

	@Test
	public void getIMDBMovieDetailsInvalid() throws Exception {
		Movie movie = xmlrpc.getIMDBMovieDetails(371746);

		assertEquals("Iron Man", movie.getName());
		assertEquals(2008, movie.getYear());
		assertEquals(371746, movie.getImdbId());
	}

	@Test
	public void detectLanguage() throws Exception {
		String text = "Only those that are prepared to fire should be fired at.";

		List<String> languages = xmlrpc.detectLanguage(text.getBytes("UTF-8"));

		assertEquals("eng", languages.get(0));
		assertTrue(1 == languages.size());
	}

	@Test
	public void fetchSubtitle() throws Exception {
		List<OpenSubtitlesSubtitleDescriptor> list = xmlrpc.searchSubtitles(singleton(Query.forImdbId(361256, -1, -1, "eng")));

		// check format
		assertEquals("srt", list.get(0).getType());

		// fetch subtitle file
		ByteBuffer data = list.get(0).fetch();

		// check size
		assertEquals(48726, data.remaining(), 100);
	}

	@Ignore
	@Test(expected = IOException.class)
	public void fetchSubtitlesExceedLimit() throws Exception {
		List<OpenSubtitlesSubtitleDescriptor> list = xmlrpc.searchSubtitles(singleton(Query.forImdbId(773262, -1, -1, "eng")));

		for (int i = 0; true; i++) {
			System.out.format("Fetch #%d: %s%n", i, list.get(i).fetch());
		}
	}

	@AfterClass
	public static void logout() throws Exception {
		// logout manually
		xmlrpc.logout();
	}

}
