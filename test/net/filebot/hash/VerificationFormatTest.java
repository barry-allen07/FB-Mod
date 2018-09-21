
package net.filebot.hash;


import static org.junit.Assert.*;

import java.io.File;
import java.util.Map.Entry;

import org.junit.Test;


public class VerificationFormatTest {

	@Test
	public void parseLine() throws Exception {
		VerificationFormat format = new VerificationFormat();

		// md5
		Entry<File, String> md5 = format.parseObject("50e85fe18e17e3616774637a82968f4c *folder/file.txt");

		assertEquals("file.txt", md5.getKey().getName());
		assertEquals("folder", md5.getKey().getParent());
		assertEquals("50e85fe18e17e3616774637a82968f4c", md5.getValue());

		// sha1
		Entry<File, String> sha1 = format.parseObject("1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA1*folder/file.txt");

		assertEquals("file.txt", sha1.getKey().getName());
		assertEquals("folder", sha1.getKey().getParent());
		assertEquals("1a02a7c1e9ac91346d08829d5037b240f42ded07", sha1.getValue());
	}
}
