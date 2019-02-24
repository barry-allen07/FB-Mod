package net.filebot.web;

import java.io.Serializable;

public class AudioTrack implements Serializable {

	protected String database;

	protected String artist;
	protected String title;
	protected String album;

	protected String albumArtist;
	protected String trackTitle;
	protected String genre;
	protected SimpleDate albumReleaseDate;
	protected Integer mediumIndex;
	protected Integer mediumCount;
	protected Integer trackIndex;
	protected Integer trackCount;

	protected String mbid; // MusicBrainz Identifier

	public AudioTrack() {
		// used by deserializer
	}

	public AudioTrack(AudioTrack other) {
		this.artist = other.artist;
		this.title = other.title;
		this.album = other.album;
		this.albumArtist = other.albumArtist;
		this.trackTitle = other.trackTitle;
		this.genre = other.genre;
		this.albumReleaseDate = other.albumReleaseDate;
		this.mediumIndex = other.mediumIndex;
		this.mediumCount = other.mediumCount;
		this.trackIndex = other.trackIndex;
		this.trackCount = other.trackCount;
		this.mbid = other.mbid;
		this.database = other.database;
	}

	public AudioTrack(String artist, String title, String album, String database) {
		this.artist = artist;
		this.title = title;
		this.album = album;
		this.database = database;
	}

	public AudioTrack(String artist, String title, String album, String albumArtist, String trackTitle, String genre, SimpleDate albumReleaseDate, Integer mediumIndex, Integer mediumCount, Integer trackIndex, Integer trackCount, String mbid, String database) {
		this.artist = artist;
		this.title = title;
		this.album = album;
		this.albumArtist = albumArtist;
		this.trackTitle = trackTitle;
		this.genre = genre;
		this.albumReleaseDate = albumReleaseDate;
		this.mediumIndex = mediumIndex;
		this.mediumCount = mediumCount;
		this.trackIndex = trackIndex;
		this.trackCount = trackCount;
		this.mbid = mbid;
		this.database = database;
	}

	public String getArtist() {
		return artist;
	}

	public String getTitle() {
		return title;
	}

	public String getAlbum() {
		return album;
	}

	public String getAlbumArtist() {
		return albumArtist;
	}

	public String getTrackTitle() {
		return trackTitle;
	}

	public String getGenre() {
		return genre;
	}

	public SimpleDate getAlbumReleaseDate() {
		return albumReleaseDate;
	}

	public Integer getMedium() {
		return mediumIndex;
	}

	public Integer getMediumCount() {
		return mediumCount;
	}

	public Integer getTrack() {
		return trackIndex;
	}

	public Integer getTrackCount() {
		return trackCount;
	}

	public String getMBID() {
		return mbid;
	}

	public String getDatabase() {
		return database;
	}

	@Override
	public AudioTrack clone() {
		return new AudioTrack(this);
	}

	@Override
	public String toString() {
		return String.format("%s - %s", getArtist(), getTitle());
	}

}
