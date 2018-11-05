package net.filebot.web;

import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;

public class FanartTVClient implements Datasource, ArtworkProvider {

	private String apikey;

	public FanartTVClient(String apikey) {
		this.apikey = apikey;
	}

	@Override
	public String getIdentifier() {
		return "FanartTV";
	}

	@Override
	public Icon getIcon() {
		return null;
	}

	public URL getResource(String path) throws Exception {
		// e.g. http://webservice.fanart.tv/v3/movies/17645?api_key=6fa42b0ef3b5f3aab6a7edaa78675ac2
		return new URL("https://webservice.fanart.tv/v3/" + path + "?api_key=" + apikey);
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Cache cache = Cache.getCache(getName(), CacheType.Weekly);
		Object json = cache.json(category + '/' + id, s -> getResource(s)).expire(Cache.ONE_WEEK).get();

		return asMap(json).entrySet().stream().flatMap(type -> {
			return streamJsonObjects(type.getValue()).map(it -> {
				try {
					String url = getString(it, "url");
					Locale language = getStringValue(it, "lang", Locale::new);
					Double likes = getDouble(it, "likes");
					String season = getString(it, "season");
					String discType = getString(it, "disc_type");

					return new Artwork(Stream.of(type.getKey(), season, discType), new URL(url), language, likes);
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
					return null;
				}
			});
		}).filter(Objects::nonNull).sorted(Artwork.RATING_ORDER).collect(toList());
	}

}
