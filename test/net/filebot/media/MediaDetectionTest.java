package net.filebot.media;

import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Locale;

import org.junit.Test;

public class MediaDetectionTest {

	@Test
	public void parseMovieYear() {
		assertEquals("[2009]", MediaDetection.parseMovieYear("Avatar 2009 2100").toString());
		assertEquals("[1955]", MediaDetection.parseMovieYear("1898 Sissi 1955").toString());
	}

	@Test
	public void stripFormatInfo() throws Exception {
		assertEquals("3.Idiots.PAL.DVD..", MediaDetection.stripFormatInfo("3.Idiots.PAL.DVD.DD5.1.x264"));
	}

	@Test
	public void detectSeriesName() throws Exception {
		assertEquals("[]", MediaDetection.detectSeriesNames(singleton(new File("Movie/LOTR.2001.AVC-1080")), false, Locale.ENGLISH).toString());
	}

	@Test
	public void grepImdbId() throws Exception {
		assertEquals("[499549]", MediaDetection.grepImdbId("@see http://www.imdb.com/title/tt0499549/").toString());
	}

	@Test
	public void grepTheTvdbId() throws Exception {
		assertEquals("[78874]", MediaDetection.grepTheTvdbId("@see http://www.thetvdb.com/?tab=series&id=78874&lid=14").toString());
		assertEquals("[78874]", MediaDetection.grepTheTvdbId("@see http://thetvdb.com/?tab=series&id=78874&lid=14").toString());
		assertEquals("[78874]", MediaDetection.grepTheTvdbId("@see https://www.thetvdb.com/?tab=seasonall&id=78874&lid=14").toString());
	}

	@Test
	public void stripReleaseInfo() throws Exception {
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.MA.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.MA.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.MA.6ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD.MA.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD.MA.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD.MA.6ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD.MA.8ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTSHDMA.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTSHDMA.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-X.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-X.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-X.6ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-X.8ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD-HRA.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-ES.6.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.6.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.1ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.2ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.6ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.7ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS.8ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTSMA"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.TrueHD.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.TrueHD.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.TrueHD.Atmos.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.TrueHD.Atmos.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DTS-HD.TrueHD.7.1.Atmos"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.4.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.1ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.2ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.4ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AC3.6ch"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.4.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP.2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP.4.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD+.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD+.2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD+.4.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD+.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP2.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP4.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DDP5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.5.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.DD.7.1"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.MP3.1.0"));
		assertEquals("Avatar 2009", MediaDetection.stripReleaseInfo("Avatar.2009.AAC.5.1"));
	}

}
