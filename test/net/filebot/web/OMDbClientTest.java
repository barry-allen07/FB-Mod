package net.filebot.web;

import static net.filebot.WebServices.*;
import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class OMDbClientTest {

	@Test
	public void searchMovie1() throws Exception {
		List<Movie> results = OMDb.searchMovie("Avatar", null);
		Movie movie = results.get(0);

		assertEquals("Avatar", movie.getName());
		assertEquals(2009, movie.getYear());
		assertEquals(499549, movie.getImdbId(), 0);
	}

	@Test
	public void searchMovie2() throws Exception {
		List<Movie> results = OMDb.searchMovie("The Terminator", null);
		Movie movie = results.get(0);

		assertEquals("The Terminator", movie.getName());
		assertEquals(1984, movie.getYear());
		assertEquals(88247, movie.getImdbId());
	}

	@Test
	public void searchMovie3() throws Exception {
		List<Movie> results = OMDb.searchMovie("Amélie", null);
		Movie movie = results.get(0);

		assertEquals("Amélie", movie.getName());
		assertEquals(2001, movie.getYear());
		assertEquals(211915, movie.getImdbId(), 0);
	}

	@Test
	public void searchMovie4() throws Exception {
		List<Movie> results = OMDb.searchMovie("Heat", null);
		Movie movie = results.get(0);

		assertEquals("Heat", movie.getName());
		assertEquals(1995, movie.getYear());
		assertEquals(113277, movie.getImdbId(), 0);
	}

	@Test
	public void searchMovie6() throws Exception {
		List<Movie> results = OMDb.searchMovie("Drive 2011", null);
		Movie movie = results.get(0);

		assertEquals("Drive", movie.getName());
		assertEquals(2011, movie.getYear());
		assertEquals(780504, movie.getImdbId(), 0);
	}

	@Test
	public void getMovieDescriptor1() throws Exception {
		Movie movie = OMDb.getMovieDescriptor(new Movie(499549), null);

		assertEquals("Avatar", movie.getName());
		assertEquals(2009, movie.getYear());
		assertEquals(499549, movie.getImdbId(), 0);
	}

	@Test
	public void getMovieDescriptor2() throws Exception {
		Movie movie = OMDb.getMovieDescriptor(new Movie(211915), null);

		assertEquals("Amélie", movie.getName());
		assertEquals(2001, movie.getYear());
		assertEquals(211915, movie.getImdbId(), 0);
	}

	@Test
	public void getMovieDescriptor3() throws Exception {
		Movie movie = OMDb.getMovieDescriptor(new Movie(75610), null);

		assertEquals("21 Up", movie.getName());
		assertEquals(1977, movie.getYear());
		assertEquals(75610, movie.getImdbId(), 0);
	}

	@Test
	public void getMovieDescriptor4() throws Exception {
		Movie movie = OMDb.getMovieDescriptor(new Movie(369702), null);

		assertEquals("The Sea Inside", movie.getName());
		assertEquals(2004, movie.getYear());
		assertEquals(369702, movie.getImdbId(), 0);
	}

	@Test
	public void getMovieDescriptor5() throws Exception {
		Movie movie = OMDb.getMovieDescriptor(new Movie(1020960), null);

		assertEquals("God, the Universe and Everything Else", movie.getName());
		assertEquals(1988, movie.getYear());
		assertEquals(1020960, movie.getImdbId(), 0);
	}

	@Test
	public void getImdbApiMovieInfoReleasedNA() throws Exception {
		MovieInfo movie = OMDb.getMovieInfo(new Movie(1287357));
		assertEquals("Sommersonntag", movie.getName());
		assertEquals(2008, movie.getReleased().getYear());
		assertEquals("2008-06-07", movie.getReleased().toString());
	}

}
