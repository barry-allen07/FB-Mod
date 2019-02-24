package net.filebot.web;

import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.StringUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.swing.Icon;

import net.filebot.ResourceManager;
import net.filebot.mediainfo.MediaInfo;
import net.filebot.mediainfo.MediaInfo.StreamKind;

public class ID3Lookup implements MusicIdentificationService {

	@Override
	public String getIdentifier() {
		return "ID3";
	}

	@Override
	public String getName() {
		return "ID3 Tags";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.mediainfo");
	}

	@Override
	public Map<File, AudioTrack> lookup(Collection<File> files) {
		return read(files, this::getAudioTrack, AUDIO_FILES, VIDEO_FILES);
	}

	private <T> Map<File, T> read(Collection<File> files, Function<MediaInfo, T> parse, FileFilter... filters) {
		Map<File, T> info = new LinkedHashMap<File, T>(files.size());

		try (MediaInfo m = new MediaInfo()) {
			for (File f : filter(files, filters)) {
				try {
					// open or throw exception
					m.open(f);

					T object = parse.apply(m);
					if (object != null) {
						info.put(f, object);
					}
				} catch (Throwable e) {
					debug.warning(e::getMessage);
				}
			}
		}

		return info;
	}

	public AudioTrack getAudioTrack(File file) {
		return read(file, this::getAudioTrack);
	}

	public Episode Episode(File file) {
		return read(file, this::getEpisode);
	}

	private <T> T read(File file, Function<MediaInfo, T> parse) {
		try (MediaInfo m = new MediaInfo()) {
			try {
				return parse.apply(m.open(file));
			} catch (Throwable e) {
				debug.warning(e::getMessage);
			}
		}

		return null;
	}

	public AudioTrack getAudioTrack(MediaInfo m) {
		// artist and song title information is required
		String artist = getString(m, "Performer", "Composer");
		String title = getString(m, "Title", "Track");

		if (artist == null || title == null) {
			return null;
		}

		// all other properties are optional
		String album = getString(m, "Album");
		String albumArtist = getString(m, "Album/Performer");
		String trackTitle = getString(m, "Track");
		String genre = getString(m, "Genre");
		Integer mediumIndex = null;
		Integer mediumCount = null;
		Integer trackIndex = getInteger(m, "Track/Position");
		Integer trackCount = getInteger(m, "Track/Position_Total");
		String mbid = getString(m, "Acoustid Id");

		// try to parse 2016-03-10
		String dateString = getString(m, "Recorded_Date");
		SimpleDate albumReleaseDate = SimpleDate.parse(dateString);

		// try to parse 2016
		if (albumReleaseDate == null) {
			Integer year = matchInteger(dateString);
			if (year != null) {
				albumReleaseDate = new SimpleDate(year, 1, 1);
			}
		}

		return new AudioTrack(artist, title, album, albumArtist, trackTitle, genre, albumReleaseDate, mediumIndex, mediumCount, trackIndex, trackCount, mbid, getIdentifier());
	}

	public Episode getEpisode(MediaInfo m) {
		String series = getString(m, "Album");
		Integer season = getInteger(m, "Season");
		Integer episode = getInteger(m, "Part", "Track/Position");
		String title = getString(m, "Title", "Track");

		if (series == null || episode == null) {
			return null;
		}

		return new Episode(series, season, episode, title);
	}

	private String getString(MediaInfo mediaInfo, String... keys) {
		for (String key : keys) {
			String value = mediaInfo.get(StreamKind.General, 0, key);
			if (value.length() > 0) {
				return value;
			}
		}
		return null;
	}

	private Integer getInteger(MediaInfo mediaInfo, String... keys) {
		return matchInteger(getString(mediaInfo, keys));
	}

}
