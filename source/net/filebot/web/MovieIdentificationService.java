package net.filebot.web;

import java.util.List;
import java.util.Locale;

public interface MovieIdentificationService extends Datasource {

	List<Movie> searchMovie(String query, Locale locale) throws Exception;

	Movie getMovieDescriptor(Movie movie, Locale locale) throws Exception;

}
