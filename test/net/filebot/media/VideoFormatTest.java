package net.filebot.media;

import static org.junit.Assert.*;

import org.junit.Test;

public class VideoFormatTest {

	VideoFormat vf = new VideoFormat();

	@Test
	public void trickyResolutions() {
		assertEquals(1080, vf.guessFormat(1920, 1040));
		assertEquals(720, vf.guessFormat(1280, 528));
		assertEquals(576, vf.guessFormat(748, 574));
	}

}
