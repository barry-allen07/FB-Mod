package net.filebot.mediainfo;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import net.filebot.mediainfo.MediaInfo.StreamKind;

@Ignore("Sample file does not exist")
public class MediaInfoTest {

	File getSampleFile(String name) throws Exception {
		File tmpdir = new File(FileUtils.getTempDirectory(), getClass().getName());
		File sample = new File(tmpdir, "big_buck_bunny_720p_1mb.mp4");
		URL data = new URL("http://www.sample-videos.com/video/mp4/720/big_buck_bunny_720p_1mb.mp4");

		if (!sample.exists()) {
			FileUtils.copyURLToFile(data, sample);
		}

		File file = new File(tmpdir, name + ".mp4");
		if (!file.exists()) {
			FileUtils.copyFile(sample, file);
		}

		return file;
	}

	void testSampleFile(String name) throws Exception {
		MediaInfo mi = new MediaInfo().open(getSampleFile(name));

		assertEquals("MPEG-4", mi.get(StreamKind.General, 0, "Format"));
		assertEquals("AVC", mi.get(StreamKind.Video, 0, "Format"));
		assertEquals("AAC", mi.get(StreamKind.Audio, 0, "Format"));
	}

	@Test
	public void open() throws Exception {
		testSampleFile("English");
	}

	@Test
	public void openUnicode() throws Exception {
		testSampleFile("中文");
		testSampleFile("日本語");
	}

	@Test
	public void openDiacriticalMarks() throws Exception {
		testSampleFile("Español");
		testSampleFile("Österreichisch");
	}

}
