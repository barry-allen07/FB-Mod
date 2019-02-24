package net.filebot.web;

import static org.junit.Assert.*;

import org.junit.Test;

public class SimpleDateTest {

	@Test
	public void parse() {
		assertEquals("2015-01-01", SimpleDate.parse("2015-1-1").toString());
		assertEquals("2015-02-02", SimpleDate.parse("2015-02-02").toString());

	}

	@Test
	public void parseIllegalDate() {
		// simple date allows illegal values
		assertEquals("2015-12-34", SimpleDate.parse("2015-12-34").toString());
	}

}
