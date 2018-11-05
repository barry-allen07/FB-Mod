package net.filebot.web;

import static net.filebot.Logging.*;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import net.filebot.util.ByteBufferOutputStream;
import net.filebot.util.FileUtilities;

/**
 * Describes a subtitle on OpenSubtitles.
 *
 * @see OpenSubtitlesXmlRpc
 */
public class OpenSubtitlesSubtitleDescriptor implements SubtitleDescriptor, Serializable {

	public static enum Property {
		IDSubtitle, IDSubtitleFile, IDSubMovieFile, IDMovie, IDMovieImdb, SubFileName, SubLastTS, SubFormat, SubEncoding, SubHash, SubSize, MovieHash, MovieByteSize, MovieName, MovieNameEng, MovieYear, MovieReleaseName, MovieTimeMS, MovieFPS, MovieImdbRating, MovieKind, SeriesSeason, SeriesEpisode, SeriesIMDBParent, SubLanguageID, ISO639, LanguageName, UserID, UserRank, UserNickName, SubAddDate, SubAuthorComment, SubFeatured, SubComments, SubDownloadsCnt, SubHearingImpaired, SubRating, SubHD, SubBad, SubActualCD, SubSumCD, MatchedBy, QueryNumber, SubtitlesLink, SubDownloadLink, ZipDownloadLink;

		public static <V> EnumMap<Property, V> asEnumMap(Map<String, V> stringMap) {
			EnumMap<Property, V> enumMap = new EnumMap<Property, V>(Property.class);

			// copy entry set to enum map
			for (Entry<String, V> entry : stringMap.entrySet()) {
				try {
					enumMap.put(Property.valueOf(entry.getKey()), entry.getValue());
				} catch (IllegalArgumentException e) {
					// illegal enum constant, just ignore
				}
			}

			return enumMap;
		}
	}

	private final Map<Property, String> properties;

	public OpenSubtitlesSubtitleDescriptor(Map<Property, String> properties) {
		this.properties = properties;
	}

	public Map<Property, String> getProperties() {
		return properties;
	}

	public String getProperty(Property key) {
		return properties.get(key);
	}

	@Override
	public String getPath() {
		return getProperty(Property.SubFileName);
	}

	@Override
	public String getName() {
		return FileUtilities.getNameWithoutExtension(getProperty(Property.SubFileName));
	}

	@Override
	public String getLanguageName() {
		return getProperty(Property.LanguageName);
	}

	@Override
	public String getType() {
		return getProperty(Property.SubFormat);
	}

	@Override
	public long getLength() {
		return Long.parseLong(getProperty(Property.SubSize));
	}

	public String getMovieHash() {
		return getProperty(Property.MovieHash);
	}

	public long getMovieByteSize() {
		return Long.parseLong(getProperty(Property.MovieByteSize));
	}

	public String getMovieReleaseName() {
		return getProperty(Property.MovieReleaseName);
	}

	public int getQueryNumber() {
		return Integer.parseInt(getProperty(Property.QueryNumber));
	}

	public float getMovieFPS() {
		return Float.parseFloat(getProperty(Property.MovieFPS));
	}

	public long getMovieTimeMS() {
		return Long.parseLong(getProperty(Property.MovieTimeMS));
	}

	public int getSubActualCD() {
		return Integer.parseInt(getProperty(Property.SubActualCD));
	}

	public int getSubSumCD() {
		return Integer.parseInt(getProperty(Property.SubSumCD));
	}

	private static int DOWNLOAD_QUOTA = 1000;

	public static synchronized void checkDownloadQuota() throws IllegalStateException {
		if (DOWNLOAD_QUOTA <= 0) {
			throw new IllegalStateException("Download-Quota has been exceeded");
		}
	}

	private static synchronized void setAndCheckDownloadQuota(int quota) throws IllegalStateException {
		DOWNLOAD_QUOTA = quota;
		checkDownloadQuota();
	}

	@Override
	public ByteBuffer fetch() throws Exception {
		checkDownloadQuota();

		URLConnection c = new URL(getProperty(Property.SubDownloadLink)).openConnection();
		try (InputStream in = c.getInputStream()) {
			// check download quota
			String quota = c.getHeaderField("Download-Quota");
			if (quota != null) {
				debug.finest("Download-Quota: " + quota);
				setAndCheckDownloadQuota(Integer.parseInt(quota));
			}

			// read and extract subtitle data
			ByteBufferOutputStream buffer = new ByteBufferOutputStream(getLength());
			buffer.transferFully(new GZIPInputStream(in));
			return buffer.getByteBuffer();
		}
	}

	@Override
	public int hashCode() {
		return getProperty(Property.IDSubtitle).hashCode();
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof OpenSubtitlesSubtitleDescriptor) {
			OpenSubtitlesSubtitleDescriptor other = (OpenSubtitlesSubtitleDescriptor) object;
			return getProperty(Property.IDSubtitle).equals(other.getProperty(Property.IDSubtitle));
		}

		return false;
	}

	@Override
	public String toString() {
		return getPath();
	}

	@Override
	public File toFile() {
		return new File(getPath());
	}

}
