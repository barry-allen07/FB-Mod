package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.OpenSubtitlesHasher.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.Cache.TypedCache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;
import net.filebot.media.MediaCharacteristics;
import net.filebot.media.MediaCharacteristicsParser;
import net.filebot.media.MediaDetection;
import net.filebot.util.ExceptionUtilities;
import net.filebot.util.Timer;
import net.filebot.web.OpenSubtitlesXmlRpc.BaseInfo;
import net.filebot.web.OpenSubtitlesXmlRpc.Query;
import net.filebot.web.OpenSubtitlesXmlRpc.SubFile;
import net.filebot.web.OpenSubtitlesXmlRpc.TryUploadResponse;
import redstone.xmlrpc.XmlRpcException;
import redstone.xmlrpc.XmlRpcFault;

/**
 * SubtitleClient for OpenSubtitles.
 */
public class OpenSubtitlesClient implements SubtitleProvider, VideoHashSubtitleService, MovieIdentificationService {

	public final OpenSubtitlesXmlRpc xmlrpc;

	private String username = "";
	private String password = "";

	public OpenSubtitlesClient(String name, String version) {
		this.xmlrpc = new OpenSubtitlesXmlRpcWithRetryAndFloodLimit(String.format("%s v%s", name, version), 2, 3000);
	}

	@Override
	public String getIdentifier() {
		return "OpenSubtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.opensubtitles");
	}

	@Override
	public URI getLink() {
		return URI.create("http://www.opensubtitles.org");
	}

	public synchronized void setUser(String username, String password_md5) {
		// cancel previous session
		this.logout();

		this.username = username;
		this.password = password_md5;
	}

	public boolean isAnonymous() {
		return username == null || username.isEmpty();
	}

	@Override
	public List<SubtitleSearchResult> search(String query) throws Exception {
		throw new UnsupportedOperationException("XMLRPC::SearchMoviesOnIMDB has been banned due to abuse");
	}

	@Override
	public List<Movie> searchMovie(String query, Locale locale) throws Exception {
		throw new UnsupportedOperationException("XMLRPC::SearchMoviesOnIMDB has been banned due to abuse");
	}

	@Override
	public synchronized List<SubtitleSearchResult> guess(String tag) throws Exception {
		// require login
		return getSearchCache("tag").computeIfAbsent(tag, it -> {
			login();
			return xmlrpc.guessMovie(singleton(tag)).getOrDefault(tag, emptyList());
		});
	}

	public synchronized List<SubtitleSearchResult> searchIMDB(String query) throws Exception {
		// require login
		return getSearchCache("query").computeIfAbsent(query, it -> {
			login();
			return xmlrpc.searchMoviesOnIMDB(query);
		});
	}

	public synchronized List<SubtitleDescriptor> getSubtitleList(Query query) throws Exception {
		// require login
		return getSubtitlesCache().computeIfAbsent(query, it -> {
			login();
			return xmlrpc.searchSubtitles(singleton(query));
		});
	}

	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, Locale locale) throws Exception {
		return getSubtitleList(searchResult, -1, -1, locale);
	}

	@Override
	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int[][] episodeFilter, Locale locale) throws Exception {
		// no filter
		if (episodeFilter == null || episodeFilter.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		int[] seasons = stream(episodeFilter).mapToInt(ii -> ii[0]).filter(i -> i >= 0).sorted().distinct().toArray();
		int[] episodes = stream(episodeFilter).mapToInt(ii -> ii[1]).filter(i -> i >= 0).sorted().distinct().toArray();

		// no filter
		if (seasons.length == 0 && episodes.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		// episode filter
		if (seasons.length == 1 && episodes.length == 1) {
			return getSubtitleList(searchResult, seasons[0], episodes[0], locale);
		}

		// season filter
		if (seasons.length > 0 && episodes.length == 0) {
			return stream(seasons).boxed().flatMap(s -> {
				try {
					return getSubtitleList(searchResult, s, -1, locale).stream();
				} catch (Exception e) {
					throw new RuntimeException(String.format("Failed to retrieve subtitle list for season: %s S%02d [%s]", searchResult, s, locale), e);
				}
			}).distinct().collect(toList());
		}

		// multi-episode filter
		return stream(episodeFilter).flatMap(ii -> {
			try {
				return getSubtitleList(searchResult, ii[0], ii[1], locale).stream();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to retrieve subtitle list for episode: %s %s [%s]", searchResult, asList(ii), locale), e);
			}
		}).distinct().collect(toList());
	}

	public synchronized List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int season, int episode, Locale locale) throws Exception {
		Query query = Query.forImdbId(searchResult.getImdbId(), season, episode, getLanguageFilter(locale));

		// require login
		return getSubtitlesCache().computeIfAbsent(query, it -> {
			login();
			return xmlrpc.searchSubtitles(singleton(query));
		});
	}

	@Override
	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Locale locale) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new HashMap<File, List<SubtitleDescriptor>>(files.length);
		Set<File> remainingFiles = new HashSet<File>(asList(files));

		// lookup subtitles by hash
		if (remainingFiles.size() > 0) {
			results.putAll(getSubtitleListByHash(remainingFiles.toArray(new File[0]), locale));
		}

		// remove files for which subtitles have already been found
		results.forEach((k, v) -> {
			if (v.size() > 0) {
				remainingFiles.remove(k);
			}
		});

		return results;
	}

	protected Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Function<File, Query> queryMapper) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new HashMap<File, List<SubtitleDescriptor>>(files.length);

		// dispatch query for all hashes
		for (File f : files) {
			Query query = queryMapper.apply(f);
			if (query != null) {
				results.put(f, getSubtitleList(query));
			} else {
				results.put(f, emptyList());
			}
		}

		return results;
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByHash(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			if (f.length() > HASH_CHUNK_SIZE) {
				try {
					String hash = computeHash(f);
					return Query.forHash(hash, f.length(), getLanguageFilter(locale));
				} catch (Exception e) {
					debug.log(Level.SEVERE, "Failed to compute hash", e);
				}
			} else {
				// debug dummy files, e.g. { "hash":"ca8395374fad4b83", "size":639511378 }
				try {
					Map<?, ?> json = asMap(readJson(readTextFile(f)));
					if (json != null) {
						return Query.forHash(json.get("hash").toString(), Long.parseLong(json.get("size").toString()), getLanguageFilter(locale));
					}
				} catch (Exception e) {
					debug.finest("Ignore sample file: " + f);
				}
			}
			return null;
		});
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByTag(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			String tag = getNameWithoutExtension(f.getName());
			return Query.forTag(tag, getLanguageFilter(locale));
		});
	}

	@Override
	public synchronized CheckResult checkSubtitle(File videoFile, File subtitleFile) throws Exception {
		// require login
		login();

		// check if subs already exist in DB
		SubFile subFile = getSubFile(videoFile, subtitleFile, false);
		TryUploadResponse response = xmlrpc.tryUploadSubtitles(subFile);

		// TryUploadResponse: false => [{HashWasAlreadyInDb=1, MovieKind=movie, IDSubtitle=3167446, MoviefilenameWasAlreadyInDb=1, ISO639=en, MovieYear=2007, SubLanguageID=eng, MovieName=Blades of Glory, MovieNameEng=, IDMovieImdb=445934}]
		boolean exists = !response.isUploadRequired();
		Movie identity = null;
		Locale language = null;

		if (response.getSubtitleData().size() > 0) {
			try {
				Map<String, String> fields = response.getSubtitleData().get(0);

				String lang = fields.get("SubLanguageID");
				language = new Locale(lang);

				String imdb = fields.get("IDMovieImdb");
				String name = fields.get("MovieName");
				String year = fields.get("MovieYear");
				identity = new Movie(name, Integer.parseInt(year), Integer.parseInt(imdb));
			} catch (Exception e) {
				debug.log(Level.SEVERE, "Failed to upload subtitles", e);
			}
		}

		return new CheckResult(exists, identity, language);

	}

	@Override
	public synchronized void uploadSubtitle(Object identity, Locale locale, File[] videoFile, File[] subtitleFile) throws Exception {
		int imdbid = -1;
		try {
			imdbid = ((Movie) identity).getImdbId();
		} catch (Exception e) {
			throw new IllegalArgumentException("Illegal Movie ID: " + identity);
		}

		String subLanguageID = getSubLanguageID(locale);

		BaseInfo info = new BaseInfo();
		info.setIDMovieImdb(imdbid);
		info.setSubLanguageID(subLanguageID);

		SubFile[] subFiles = new SubFile[videoFile.length];
		for (int i = 0; i < subFiles.length; i++) {
			subFiles[i] = getSubFile(videoFile[i], subtitleFile[i], true);
		}

		// require login
		login();

		xmlrpc.uploadSubtitles(info, subFiles);
	}

	protected SubFile getSubFile(File videoFile, File subtitleFile, boolean content) throws IOException {
		// subhash (md5 of subtitles), subfilename, moviehash, moviebytesize, moviefilename
		SubFile sub = new SubFile();
		sub.setSubHash(md5(readFile(subtitleFile)));
		sub.setSubFileName(subtitleFile.getName());
		sub.setMovieHash(computeHash(videoFile));
		sub.setMovieByteSize(videoFile.length());
		sub.setMovieFileName(videoFile.getName());

		// encode subtitle contents
		if (content) {
			sub.setSubContent(readFile(subtitleFile));
		}

		try (MediaCharacteristics mi = MediaCharacteristicsParser.open(videoFile)) {
			sub.setMovieFPS(String.valueOf(mi.getFrameRate()));
			sub.setMovieTimeMS(String.valueOf(mi.getDuration().toMillis()));
		} catch (Throwable e) {
			debug.log(Level.SEVERE, "Failed to read media info", e);
		}

		return sub;
	}

	@Override
	public synchronized Movie getMovieDescriptor(Movie id, Locale locale) throws Exception {
		if (id.getImdbId() <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + id.getImdbId());
		}

		// require login
		return getLookupCache(locale).computeIfAbsent(id.getImdbId(), it -> {
			login();
			return xmlrpc.getIMDBMovieDetails(id.getImdbId());
		});
	}

	public Movie getMovieDescriptor(File movieFile, Locale locale) throws Exception {
		return getMovieDescriptors(singleton(movieFile), locale).get(movieFile);
	}

	public synchronized Map<File, Movie> getMovieDescriptors(Collection<File> movieFiles, Locale locale) throws Exception {
		// create result array
		Map<File, Movie> results = new HashMap<File, Movie>();

		// make sure we don't get mismatches by making sure the hash has not been confirmed numerous times
		int minSeenCount = 20;

		for (File f : movieFiles) {
			if (f.length() > HASH_CHUNK_SIZE) {
				String hash = computeHash(f);

				Movie match = getLookupCache(locale).computeIfAbsent(hash, it -> {
					return xmlrpc.checkMovieHash(singleton(hash), minSeenCount).get(hash);
				});

				results.put(f, match);
			}
		}

		return results;
	}

	@Override
	public URI getSubtitleListLink(SubtitleSearchResult searchResult, Locale locale) {
		return URI.create(String.format("http://www.opensubtitles.org/en/search/imdbid-%d/sublanguageid-%s", searchResult.getImdbId(), getSubLanguageID(locale)));
	}

	public synchronized Locale detectLanguage(byte[] data) throws Exception {
		if (data.length < 256) {
			throw new IllegalArgumentException("Data is too small: " + data.length);
		}

		// require login
		List<String> languages = getCache("detect").castList(String.class).computeIfAbsent(md5(data), it -> {
			login();
			return xmlrpc.detectLanguage(data);
		});

		return languages.size() > 0 ? new Locale(languages.get(0)) : Locale.ROOT;
	}

	public synchronized void login() throws Exception {
		if (!xmlrpc.isLoggedOn()) {
			xmlrpc.login(username, password, "en");
		}
		logoutTimer.set(10, TimeUnit.MINUTES, true);
	}

	public synchronized void logout() {
		if (xmlrpc.isLoggedOn()) {
			try {
				xmlrpc.logout();
			} catch (Exception e) {
				debug.log(Level.WARNING, "Failed to log out", e);
			}
		}
		logoutTimer.cancel();
	}

	protected final Timer logoutTimer = new Timer() {

		@Override
		public void run() {
			logout();
		}
	};

	public synchronized Map<?, ?> getServerInfo() throws Exception {
		// require login
		login();

		return xmlrpc.getServerInfo();
	}

	public Map<?, ?> getDownloadLimits() throws Exception {
		return (Map<?, ?>) getServerInfo().get("download_limits");
	}

	/**
	 * SubLanguageID by English language name
	 */
	protected synchronized Map<String, String> getSubLanguageMap() throws Exception {
		Map<String, String> subLanguageMap = new HashMap<String, String>();

		// try to get language map from cache
		Cache cache = Cache.getCache(getName() + "_languages", CacheType.Persistent);
		Map<?, ?> m = (Map<?, ?>) cache.computeIfAbsent("subLanguageMap", k -> xmlrpc.getSubLanguages());

		// add additional language aliases for improved compatibility
		Map<String, Locale> additionalLanguageMappings = MediaDetection.releaseInfo.getLanguageMap(Locale.ENGLISH);

		m.forEach((k, v) -> {
			// map id by name
			String subLanguageID = k.toString().toLowerCase();
			String languageCode = v.toString().toLowerCase();

			subLanguageMap.put(languageCode, subLanguageID);
			subLanguageMap.put(subLanguageID, subLanguageID); // add reverse mapping as well for improved compatibility

			// add additional language aliases for improved compatibility
			for (String key : new String[] { subLanguageID, languageCode }) {
				Locale locale = additionalLanguageMappings.get(key);
				if (locale != null) {
					for (String identifier : asList(locale.getLanguage(), locale.getISO3Language(), locale.getDisplayLanguage(Locale.ENGLISH))) {
						if (identifier != null && identifier.length() > 0 && !subLanguageMap.containsKey(identifier.toLowerCase())) {
							subLanguageMap.put(identifier.toLowerCase(), subLanguageID);
						}
					}
				}
			}
		});

		return subLanguageMap;
	}

	protected String getSubLanguageID(Locale locale) {
		if (locale == null || locale.equals(Locale.ROOT)) {
			return "all";
		}

		// some special handling
		switch (locale.toString()) {
		case "en_US":
			return "eng"; // English
		case "pt_BR":
			return "pob"; // Brazilian Portuguese
		case "zh_CN":
			return "chi"; // Chinese (Simplified)
		case "zh_TW":
			return "zht"; // Chinese (Traditional)
		case "iw_IL":
			return "heb"; // Hebrew
		}

		Map<String, String> languageMap;
		try {
			languageMap = getSubLanguageMap();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to retrieve subtitle language map", e);
		}

		String subLanguageID = languageMap.get(locale.getLanguage());
		if (subLanguageID == null) {
			throw new IllegalArgumentException("SubLanguageID not found: " + locale);
		}

		return subLanguageID;
	}

	protected String[] getLanguageFilter(Locale locale) {
		return locale == null || locale.getLanguage().isEmpty() ? new String[0] : new String[] { getSubLanguageID(locale) };
	}

	public Cache getCache(String section) {
		return Cache.getCache(getName() + "_" + section, CacheType.Daily);
	}

	protected TypedCache<List<SubtitleSearchResult>> getSearchCache(String method) {
		return getCache("search_" + method).castList(SubtitleSearchResult.class);
	}

	protected TypedCache<List<SubtitleDescriptor>> getSubtitlesCache() {
		return getCache("data").castList(SubtitleDescriptor.class);
	}

	protected TypedCache<Movie> getLookupCache(Locale locale) {
		return getCache("lookup_" + locale).cast(Movie.class);
	}

	protected static class OpenSubtitlesXmlRpcWithRetryAndFloodLimit extends OpenSubtitlesXmlRpc {

		private final Object lock = new Object();

		private int retryCountLimit;
		private long retryWaitTime;

		public OpenSubtitlesXmlRpcWithRetryAndFloodLimit(String useragent, int retryCountLimit, long retryWaitTime) {
			super(useragent);
			this.retryCountLimit = retryCountLimit;
			this.retryWaitTime = retryWaitTime;
		}

		@Override
		protected Map<?, ?> invoke(String method, Object... arguments) throws XmlRpcFault {
			for (int i = 0; retryCountLimit < 0 || i <= retryCountLimit; i++) {
				try {
					if (i > 0) {
						Thread.sleep(retryWaitTime);
					}

					// only allow 1 single concurrent connection at any time (to reduce abuse)
					synchronized (lock) {
						return super.invoke(method, arguments);
					}
				} catch (XmlRpcException e) {
					IOException ioException = ExceptionUtilities.findCause(e, IOException.class);
					if (ioException == null || i >= 0 && i >= retryCountLimit) {
						throw e;
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			return null; // can't happen
		}

	}

}
