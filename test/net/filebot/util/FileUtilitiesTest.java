
package net.filebot.util;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

public class FileUtilitiesTest {

	@Test
	public void hasExtension() {
		assertTrue(FileUtilities.hasExtension("abc.txt", "txt"));
		assertFalse(FileUtilities.hasExtension(".hidden", "txt"));
	}

	@Test
	public void getExtension() {
		assertEquals("txt", FileUtilities.getExtension("abc.txt"));
		assertEquals("out", FileUtilities.getExtension("a.out"));
		assertEquals(null, FileUtilities.getExtension(".hidden"));
		assertEquals(null, FileUtilities.getExtension("a."));

		assertEquals("r00", FileUtilities.getExtension("archive.r00"));
		assertEquals(null, FileUtilities.getExtension("archive.r??"));
		assertEquals(null, FileUtilities.getExtension("archive.invalid extension"));
	}

	@Test
	public void getNameWithoutExtension() {
		assertEquals("abc", FileUtilities.getNameWithoutExtension("abc.txt"));
		assertEquals("a", FileUtilities.getNameWithoutExtension("a.out"));
		assertEquals(".hidden", FileUtilities.getNameWithoutExtension(".hidden"));
		assertEquals("a.", FileUtilities.getNameWithoutExtension("a."));

		assertEquals("archive", FileUtilities.getNameWithoutExtension("archive.r00"));
		assertEquals("archive.r??", FileUtilities.getNameWithoutExtension("archive.r??"));
		assertEquals("archive.invalid extension", FileUtilities.getNameWithoutExtension("archive.invalid extension"));
	}

	@Test
	public void isDerived() {
		assertTrue(FileUtilities.isDerived(new File("avatar.eng.srt"), new File("avatar.mp4")));
		assertTrue(FileUtilities.isDerived(new File("1.z"), new File("1.xyz")));
		assertTrue(FileUtilities.isDerived(new File("1.xyz"), new File("1.z")));
		assertFalse(FileUtilities.isDerived(new File("1.eng.srt"), new File("10.mp4")));
		assertFalse(FileUtilities.isDerived(new File("10.z"), new File("1.mp4")));
	}

	@Test
	public void normalizePathSeparators() {
		assertEquals("C:/file.txt", FileUtilities.normalizePathSeparators("C:\\file.txt"));
		assertEquals("/Volume/USB/file.txt", FileUtilities.normalizePathSeparators("/Volume\\USB/file.txt"));

		assertEquals("\\\\server/share/data/file.txt", FileUtilities.normalizePathSeparators("\\\\server\\share\\data\\file.txt"));
		assertEquals("\\\\server/share/data/file.txt", FileUtilities.normalizePathSeparators("\\\\server\\share\\data\\file.txt"));
		assertEquals("/server/share/data/file.txt", FileUtilities.normalizePathSeparators("//server/share/data/file.txt"));
	}

}
