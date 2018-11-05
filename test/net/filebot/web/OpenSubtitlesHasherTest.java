
package net.filebot.web;


import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class OpenSubtitlesHasherTest {

	private String expectedHash;
	private File file;


	public OpenSubtitlesHasherTest(String expectedHash, File file) {
		this.file = file;
		this.expectedHash = expectedHash;
	}


	@Parameters
	public static Collection<Object[]> parameters() {
		Object[][] parameters = new Object[3][];

		parameters[0] = new Object[] { "8e245d9679d31e12", new File("breakdance.avi") };
		parameters[1] = new Object[] { "61f7751fc2a72bfb", new File("dummy.bin") };
		parameters[2] = new Object[] { "a79fa10ba3b31395", new File("mini.txt") };

		return Arrays.asList(parameters);
	}


	@Test
	public void computeHashFile() throws Exception {
		assertEquals(expectedHash, OpenSubtitlesHasher.computeHash(file));
	}


	@Test
	public void computeHashStream() throws Exception {
		assertEquals(expectedHash, OpenSubtitlesHasher.computeHash(new FileInputStream(file), file.length()));
	}

}
