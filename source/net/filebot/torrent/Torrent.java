package net.filebot.torrent;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;

public class Torrent {

	private String name;
	private String encoding;
	private String createdBy;
	private String announce;
	private String comment;
	private Long creationDate;
	private Long pieceLength;

	private List<FileInfo> files;
	private boolean singleFileTorrent;

	protected Torrent() {
		// used by serializer
	}

	public Torrent(File torrent) throws IOException {
		this(decodeTorrent(torrent));
	}

	public Torrent(Map<?, ?> torrentMap) {
		createdBy = getString(torrentMap.get("created by"));
		announce = getString(torrentMap.get("announce"));
		comment = getString(torrentMap.get("comment"));
		creationDate = getLong(torrentMap.get("creation date"));

		Map<?, ?> info = getMap(torrentMap.get("info"));
		name = getString(info.get("name"));
		pieceLength = getLong(info.get("piece length"));

		if (info.containsKey("files")) {
			// torrent contains multiple entries
			singleFileTorrent = false;
			files = getList(info.get("files")).stream().map(this::getMap).map(f -> {
				String path = getList(f.get("path")).stream().map(Object::toString).collect(joining("/"));
				long length = getLong(f.get("length"));
				return new SimpleFileInfo(path, length);
			}).collect(toList());
		} else {
			// torrent contains only a single entry
			singleFileTorrent = true;
			files = singletonList(new SimpleFileInfo(name, getLong(info.get("length"))));
		}
	}

	private static Map<?, ?> decodeTorrent(File torrent) throws IOException {
		byte[] bytes = Files.readAllBytes(torrent.toPath());

		return new Bencode().decode(bytes, Type.DICTIONARY);
	}

	private String getString(Object value) {
		if (value instanceof CharSequence) {
			return value.toString();
		}
		return "";
	}

	private long getLong(Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return -1;
	}

	private Map<?, ?> getMap(Object value) {
		if (value instanceof Map) {
			return (Map) value;
		}
		return emptyMap();
	}

	private List<?> getList(Object value) {
		if (value instanceof List) {
			return (List) value;
		}
		return emptyList();
	}

	public String getAnnounce() {
		return announce;
	}

	public String getComment() {
		return comment;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Long getCreationDate() {
		return creationDate;
	}

	public String getEncoding() {
		return encoding;
	}

	public List<FileInfo> getFiles() {
		return unmodifiableList(files);
	}

	public String getName() {
		return name;
	}

	public Long getPieceLength() {
		return pieceLength;
	}

	public boolean isSingleFileTorrent() {
		return singleFileTorrent;
	}

}
