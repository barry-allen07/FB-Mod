package net.filebot.web;

import java.util.List;
import java.util.Locale;

public interface ArtworkProvider {

	List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception;

}
