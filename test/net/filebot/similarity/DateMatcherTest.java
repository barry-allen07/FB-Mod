package net.filebot.similarity;

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;

public class DateMatcherTest {

	DateMatcher m = new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.ENGLISH);

	@Test
	public void parse() {
		assertEquals("2010-10-24", m.match("2010-10-24").toString());
		assertEquals("2009-06-01", m.match("2009/6/1").toString());
		assertEquals("2010-01-01", m.match("1.1.2010").toString());
		assertEquals("2010-06-01", m.match("01/06/2010").toString());
		assertEquals("2015-10-05", m.match("2015.October.05").toString());
		assertEquals("2015-10-06", m.match("2015.Oct.6").toString());
		assertEquals("2014-07-25", m.match("25 July 2014").toString());
		assertEquals("2015-09-08", m.match("8 Sep 2015").toString());
		assertEquals("2014-04-08", m.match("20140408").toString());
	}

	@Test
	public void parseLocale() {
		assertEquals("2016-03-01", new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.GERMAN).match("01 März 2016").toString());
		assertEquals("2016-03-02", new DateMatcher(DateMatcher.DEFAULT_SANITY, Locale.ENGLISH, Locale.GERMAN).match("01 März 2016 to 2 March 2016").toString());
	}

	@Test
	public void parseIllegal() {
		assertEquals(null, m.match("2000-01-32"));
		assertEquals(null, m.match("123456789"));
	}

	@Test
	public void sanity() {
		assertEquals(null, m.match("1911-01-01")); // too low
		assertEquals(null, m.match("2099-01-01")); // too high
	}

}
