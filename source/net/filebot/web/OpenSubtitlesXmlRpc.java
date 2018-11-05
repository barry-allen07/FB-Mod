package net.filebot.web;

import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.util.StringUtilities.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import net.filebot.util.ByteBufferOutputStream;
import net.filebot.web.OpenSubtitlesSubtitleDescriptor.Property;
import redstone.xmlrpc.XmlRpcClient;
import redstone.xmlrpc.XmlRpcException;
import redstone.xmlrpc.XmlRpcFault;
import redstone.xmlrpc.util.Base64;

public class OpenSubtitlesXmlRpc {

	private final String useragent;

	private String token;

	public OpenSubtitlesXmlRpc(String useragent) {
		this.useragent = useragent;
	}

	/**
	 * Login as anonymous user
	 */
	public void loginAnonymous() throws XmlRpcFault {
		login("", "", "en");
	}

	/**
	 * This will login user. This method should be called always when starting talking with server.
	 *
	 * @param username
	 *            username (blank for anonymous user)
	 * @param password
	 *            password (blank for anonymous user)
	 * @param language
	 *            ISO639 2-letter codes as language and later communication will be done in this language if applicable (error codes and so on).
	 */
	public synchronized void login(String username, String password, String language) throws XmlRpcFault {
		Map<?, ?> response = invoke("LogIn", username, password, language, useragent);

		// set session token
		token = response.get("token").toString();
	}

	/**
	 * This will logout user (ends session id). Call this function is before closing the client program.
	 */
	public synchronized void logout() throws XmlRpcFault {
		try {
			invoke("LogOut", token);
		} catch (XmlRpcFault e) {
			// anonymous users will always get an 401 Unauthorized when trying to logout,
			// so we ignore the status of the logout response
		} finally {
			token = null;
		}
	}

	public boolean isLoggedOn() {
		return token != null;
	}

	public Map<String, String> getServerInfo() throws XmlRpcFault {
		return (Map<String, String>) invoke("ServerInfo", token);
	}

	public List<OpenSubtitlesSubtitleDescriptor> searchSubtitles(Collection<Query> queryList) throws XmlRpcFault {
		// abort immediately if download quota has been exceeded
		OpenSubtitlesSubtitleDescriptor.checkDownloadQuota();

		List<OpenSubtitlesSubtitleDescriptor> subtitles = new ArrayList<OpenSubtitlesSubtitleDescriptor>();
		Map<?, ?> response = invoke("SearchSubtitles", token, queryList);

		try {
			List<Map<String, String>> subtitleData = (List<Map<String, String>>) response.get("data");

			for (Map<String, String> propertyMap : subtitleData) {
				subtitles.add(new OpenSubtitlesSubtitleDescriptor(Property.asEnumMap(propertyMap)));
			}
		} catch (ClassCastException e) {
			// no subtitle have been found
		}

		return subtitles;
	}

	public List<SubtitleSearchResult> searchMoviesOnIMDB(String query) throws XmlRpcFault {
		try {
			// search for movies / series
			Map<?, ?> response = invoke("SearchMoviesOnIMDB", token, query);

			List<Map<String, String>> movieData = (List<Map<String, String>>) response.get("data");
			List<SubtitleSearchResult> movies = new ArrayList<SubtitleSearchResult>();

			// title pattern
			Pattern pattern = Pattern.compile("(.+)[(](\\d{4})([/]I+)?[)]");

			for (Map<String, String> movie : movieData) {
				try {
					String imdbid = movie.get("id");
					if (!imdbid.matches("\\d{1,7}"))
						throw new IllegalArgumentException("Illegal IMDb movie ID: Must be a 7-digit number");

					// match movie name and movie year from search result
					Matcher matcher = pattern.matcher(movie.get("title"));
					if (!matcher.find())
						throw new IllegalArgumentException("Illegal title: Must be in 'name (year)' format");

					String name = matcher.group(1).replaceAll("\"", "").trim();
					int year = Integer.parseInt(matcher.group(2));

					movies.add(new SubtitleSearchResult(Integer.parseInt(imdbid), name, year, null, -1));
				} catch (Exception e) {
					debug.log(Level.FINE, String.format("Ignore movie [%s]: %s", movie, e.getMessage()));
				}
			}

			return movies;
		} catch (ClassCastException e) {
			// unexpected xmlrpc responses (e.g. error messages instead of results) will trigger this
			throw new XmlRpcException("Illegal XMLRPC response on searchMoviesOnIMDB");
		}
	}

	public Movie getIMDBMovieDetails(int imdbid) throws XmlRpcFault {
		Map<?, ?> response = invoke("GetIMDBMovieDetails", token, imdbid);

		try {
			Map<String, String> data = (Map<String, String>) response.get("data");

			String name = data.get("title");
			int year = Integer.parseInt(data.get("year"));

			return new Movie(name, year, imdbid);
		} catch (RuntimeException e) {
			// ignore, invalid response
			debug.log(Level.WARNING, String.format("Failed to lookup movie by imdbid %s: %s", imdbid, e.getMessage()));
		}

		return null;
	}

	private Map<String, Object> getUploadStruct(BaseInfo baseInfo, SubFile... subtitles) {
		Map<String, Object> struct = new LinkedHashMap<String, Object>();

		// put baseinfo
		if (baseInfo != null) {
			struct.put("baseinfo", baseInfo);
		}

		for (int i = 0; i < subtitles.length; i++) {
			struct.put("cd" + (i + 1), subtitles[i]);
		}

		return struct;
	}

	public TryUploadResponse tryUploadSubtitles(SubFile... subtitles) throws XmlRpcFault {
		Map<String, Object> struct = getUploadStruct(null, subtitles);

		Map<?, ?> response = invoke("TryUploadSubtitles", token, struct);

		boolean uploadRequired = response.get("alreadyindb").toString().equals("0");
		List<Map<String, String>> subtitleData = new ArrayList<Map<String, String>>();

		if (response.get("data") instanceof Map) {
			subtitleData.add((Map<String, String>) response.get("data"));
		} else if (response.get("data") instanceof List) {
			subtitleData.addAll((List<Map<String, String>>) response.get("data"));
		}

		return new TryUploadResponse(uploadRequired, subtitleData);
	}

	public URI uploadSubtitles(BaseInfo baseInfo, SubFile... subtitles) throws XmlRpcFault {
		Map<String, Object> struct = getUploadStruct(baseInfo, subtitles);

		Map<?, ?> response = invoke("UploadSubtitles", token, struct);

		// subtitle link
		return URI.create(response.get("data").toString());
	}

	public List<String> detectLanguage(byte[] data) throws XmlRpcFault {
		// compress and base64 encode
		String parameter = encodeData(data);

		Map<String, Map<String, String>> response = (Map<String, Map<String, String>>) invoke("DetectLanguage", token, singleton(parameter));
		List<String> languages = new ArrayList<String>(2);

		if (response.containsKey("data")) {
			languages.addAll(response.get("data").values());
		}

		return languages;
	}

	public Map<String, Integer> checkSubHash(Collection<String> hashes) throws XmlRpcFault {
		Map<?, ?> response = invoke("CheckSubHash", token, hashes);

		Map<String, ?> subHashData = (Map<String, ?>) response.get("data");
		Map<String, Integer> subHashMap = new HashMap<String, Integer>();

		for (Entry<String, ?> entry : subHashData.entrySet()) {
			// non-existing subtitles are represented as Integer 0, not String "0"
			subHashMap.put(entry.getKey(), Integer.parseInt(entry.getValue().toString()));
		}

		return subHashMap;
	}

	public Map<String, List<SubtitleSearchResult>> guessMovie(Collection<String> tags) throws XmlRpcFault {
		Map<String, List<SubtitleSearchResult>> results = new HashMap<String, List<SubtitleSearchResult>>();

		Map<?, ?> response = invoke("GuessMovieFromString", token, tags);
		Object payload = response.get("data");

		if (payload instanceof Map) {
			Map<String, Map<String, Map<String, ?>>> dataByTag = (Map<String, Map<String, Map<String, ?>>>) payload;
			for (String tag : tags) {
				List<SubtitleSearchResult> value = new ArrayList<SubtitleSearchResult>();
				Map<String, Map<String, ?>> data = dataByTag.get(tag);
				if (data != null) {
					Map<String, ?> match = data.get("BestGuess");
					if (match != null) {
						String name = String.valueOf(match.get("MovieName"));
						String kind = String.valueOf(match.get("MovieKind"));
						int imdbid = Integer.parseInt(String.valueOf(match.get("IDMovieIMDB")));
						int year = Integer.parseInt(String.valueOf(match.get("MovieYear")));
						value.add(new SubtitleSearchResult(imdbid, name, year, kind, -1));
					}
				}
				results.put(tag, value);
			}
		}

		return results;
	}

	public Map<String, Movie> checkMovieHash(Collection<String> hashes, int minSeenCount) throws XmlRpcFault {
		Map<String, Movie> movieHashMap = new HashMap<String, Movie>();

		Map<?, ?> response = invoke("CheckMovieHash2", token, hashes);
		Object payload = response.get("data");

		if (payload instanceof Map) {
			Map<String, ?> movieHashData = (Map<String, ?>) payload;
			for (Entry<String, ?> entry : movieHashData.entrySet()) {
				// empty associative arrays are deserialized as array, not as map
				if (entry.getValue() instanceof List) {
					String hash = entry.getKey();
					List<Movie> matches = new ArrayList<Movie>();

					List<?> hashMatches = (List<?>) entry.getValue();
					for (Object match : hashMatches) {
						if (match instanceof Map) {
							Map<String, String> info = (Map<String, String>) match;
							int seenCount = Integer.parseInt(info.get("SeenCount"));

							// require minimum SeenCount before this hash match is considered trusted
							if (seenCount >= minSeenCount) {
								String name = info.get("MovieName");
								int year = Integer.parseInt(info.get("MovieYear"));
								int imdb = Integer.parseInt(info.get("MovieImdbID"));

								matches.add(new Movie(name, year, imdb));
							}
						}
					}

					if (matches.size() == 1) {
						// perfect unambiguous match
						movieHashMap.put(hash, matches.get(0));
					} else if (matches.size() > 1) {
						// multiple hash matches => ignore all
						debug.log(Level.WARNING, "Ignore hash match due to hash collision: " + matches);
					}
				}
			}
		}

		return movieHashMap;
	}

	public Map<String, String> getSubLanguages() throws XmlRpcFault {
		return getSubLanguages("en");
	}

	public Map<String, String> getSubLanguages(String languageCode) throws XmlRpcFault {
		Map<String, List<Map<String, String>>> response = (Map<String, List<Map<String, String>>>) invoke("GetSubLanguages", languageCode);

		Map<String, String> subLanguageMap = new HashMap<String, String>();

		for (Map<String, String> language : response.get("data")) {
			subLanguageMap.put(language.get("SubLanguageID"), language.get("ISO639"));
		}

		return subLanguageMap;
	}

	public void noOperation() throws XmlRpcFault {
		invoke("NoOperation", token);
	}

	protected Map<?, ?> invoke(String method, Object... arguments) throws XmlRpcFault {
		try {
			XmlRpcClient rpc = new XmlRpcClient(getXmlRpcUrl(), false) {
				@Override
				public void parse(InputStream input) throws XmlRpcException {
					try {
						super.parse(new GZIPInputStream(input));
					} catch (IOException e) {
						throw new XmlRpcException(e.getMessage(), e);
					}
				};
			};
			rpc.setRequestProperty("Accept-Encoding", "gzip");

			Map<?, ?> response = (Map<?, ?>) rpc.invoke(method, arguments);
			checkResponse(response);

			return response;
		} catch (XmlRpcFault e) {
			// invalidate session token if session has expired
			if (e.getErrorCode() == 406)
				token = null;

			// rethrow exception
			throw e;
		} catch (ClassCastException e) {
			throw new XmlRpcFault(500, "The remote server returned an unexpected response");
		}
	}

	protected URL getXmlRpcUrl() {
		try {
			return new URL(System.getProperty("net.filebot.OpenSubtitlesXmlRpc.url", "https://api.opensubtitles.org/xml-rpc"));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	protected static String encodeData(byte[] data) {
		try {
			DeflaterInputStream compressedDataStream = new DeflaterInputStream(new ByteArrayInputStream(data));

			// compress data
			ByteBufferOutputStream buffer = new ByteBufferOutputStream(data.length);
			buffer.transferFully(compressedDataStream);

			// base64 encode
			return new String(Base64.encode(buffer.getByteArray()));
		} catch (IOException e) {
			// will never happen
			throw new RuntimeException(e);
		}
	}

	/**
	 * Check whether status is OK or not
	 *
	 * @param status
	 *            status code and message (e.g. 200 OK, 401 Unauthorized, ...)
	 * @throws XmlRpcFault
	 *             thrown if status code is not OK
	 */
	protected void checkResponse(Map<?, ?> response) throws XmlRpcFault {
		String status = (String) response.get("status");

		// if there is no status at all, assume everything was OK
		if (status == null || status.equals("200 OK")) {
			return;
		}

		try {
			throw new XmlRpcFault(new Scanner(status).nextInt(), status);
		} catch (NoSuchElementException e) {
			throw new XmlRpcException("Illegal status code: " + status);
		}
	}

	public static final class Query extends HashMap<String, Object> implements Serializable {

		private Query(String... sublanguageids) {
			put("sublanguageid", join(sublanguageids, ","));
		}

		public static Query forHash(String moviehash, long moviebytesize, String... sublanguageids) {
			Query query = new Query(sublanguageids);
			query.put("moviehash", moviehash);
			query.put("moviebytesize", Long.toString(moviebytesize));
			return query;
		}

		public static Query forTag(String tag, String... sublanguageids) {
			Query query = new Query(sublanguageids);
			query.put("tag", tag);
			return query;
		}

		public static Query forImdbId(int imdbid, int season, int episode, String... sublanguageids) {
			Query query = new Query(sublanguageids);
			query.put("imdbid", Integer.toString(imdbid));
			if (season >= 0) {
				query.put("season", Integer.toString(season));
			}
			if (episode >= 0) {
				query.put("episode", Integer.toString(episode));
			}
			return query;
		}
	}

	public static final class BaseInfo extends HashMap<String, Object> {

		public void setIDMovieImdb(int imdb) {
			put("idmovieimdb", Integer.toString(imdb));
		}

		public void setSubLanguageID(String sublanguageid) {
			put("sublanguageid", sublanguageid);
		}

		public void setMovieReleaseName(String moviereleasename) {
			put("moviereleasename", moviereleasename);
		}

		public void setMovieAka(String movieaka) {
			put("movieaka", movieaka);
		}

		public void setSubAuthorComment(String subauthorcomment) {
			put("subauthorcomment", subauthorcomment);
		}
	}

	public static final class SubFile extends HashMap<String, Object> {

		public void setSubHash(String subhash) {
			put("subhash", subhash);
		}

		public void setSubFileName(String subfilename) {
			put("subfilename", subfilename);
		}

		public void setMovieHash(String moviehash) {
			put("moviehash", moviehash);
		}

		public void setMovieByteSize(long moviebytesize) {
			put("moviebytesize", Long.toString(moviebytesize));
		}

		public void setMovieFileName(String moviefilename) {
			put("moviefilename", moviefilename);
		}

		public void setSubContent(byte[] data) {
			put("subcontent", encodeData(data));
		}

		public void setMovieTimeMS(String movietimems) {
			if (movietimems.length() > 0) {
				put("movietimems", movietimems);
			}
		}

		public void setMovieFPS(String moviefps) {
			if (moviefps.length() > 0) {
				put("moviefps", moviefps);
			}
		}

		public void setMovieFrames(String movieframes) {
			if (movieframes.length() > 0) {
				put("movieframes", movieframes);
			}
		}

		@Override
		public String toString() {
			return String.format("(%s, %s)", get("moviefilename"), get("subfilename"));
		}

	}

	public static final class TryUploadResponse {

		private final boolean uploadRequired;

		private final List<Map<String, String>> subtitleData;

		private TryUploadResponse(boolean uploadRequired, List<Map<String, String>> subtitleData) {
			this.uploadRequired = uploadRequired;
			this.subtitleData = subtitleData;
		}

		public boolean isUploadRequired() {
			return uploadRequired;
		}

		public List<Map<String, String>> getSubtitleData() {
			return subtitleData;
		}

		@Override
		public String toString() {
			return String.format("TryUploadResponse: %s => %s", uploadRequired, subtitleData);
		}
	}

}
