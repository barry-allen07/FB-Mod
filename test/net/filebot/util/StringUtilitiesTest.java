package net.filebot.util;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class StringUtilitiesTest {

	@Test
	public void matchInteger() {
		Integer n = StringUtilities.matchInteger("1091_20150217210000");

		assertEquals("1091", n.toString());
	}

	@Test
	public void matchIntegers() {
		List<Integer> n = StringUtilities.matchIntegers("1091_20150217210000");

		assertEquals("[1091]", n.toString());
	}

}
