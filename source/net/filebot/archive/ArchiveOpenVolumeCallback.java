package net.filebot.archive;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

class ArchiveOpenVolumeCallback implements IArchiveOpenVolumeCallback, IArchiveOpenCallback, Closeable {

	/**
	 * Cache for opened file streams
	 */
	private Map<String, RandomAccessFile> openedRandomAccessFileList = new HashMap<String, RandomAccessFile>();

	/**
	 * Name of the last volume returned by {@link #getStream(String)}
	 */
	private String name;

	/**
	 * This method should at least provide the name of the last opened volume (propID=PropID.NAME).
	 *
	 * @see IArchiveOpenVolumeCallback#getProperty(PropID)
	 */
	@Override
	public Object getProperty(PropID propID) throws SevenZipException {
		switch (propID) {
		case NAME:
			return name;
		default:
			return null;
		}
	}

	/**
	 * The name of the required volume will be calculated out of the name of the first volume and a volume index. In case of RAR file, the substring ".partNN." in the name of the volume file will indicate a volume with id NN. For example:
	 * <ul>
	 * <li>test.rar - single part archive or multi-part archive with a single volume</li>
	 * <li>test.part23.rar - 23-th part of a multi-part archive</li>
	 * <li>test.part001.rar - first part of a multi-part archive. "00" indicates, that at least 100 volumes must exist.</li>
	 * </ul>
	 */
	@Override
	public IInStream getStream(String filename) throws SevenZipException {
		try {
			// We use caching of opened streams, so check cache first
			RandomAccessFile randomAccessFile = openedRandomAccessFileList.get(filename);
			if (randomAccessFile != null) { // Cache hit.
				// Move the file pointer back to the beginning
				// in order to emulating new stream
				randomAccessFile.seek(0);

				// Save current volume name in case getProperty() will be called
				name = filename;

				return new RandomAccessFileInStream(randomAccessFile);
			}

			// Nothing useful in cache. Open required volume.
			randomAccessFile = new RandomAccessFile(filename, "r");

			// Put new stream in the cache
			openedRandomAccessFileList.put(filename, randomAccessFile);

			// Save current volume name in case getProperty() will be called
			name = filename;
			return new RandomAccessFileInStream(randomAccessFile);
		} catch (FileNotFoundException fileNotFoundException) {
			// Required volume doesn't exist. This happens if the volume:
			// 1. never exists. 7-Zip doesn't know how many volumes should
			// exist, so it have to try each volume.
			// 2. should be there, but doesn't. This is an error case.

			// Since normal and error cases are possible,
			// we can't throw an error message
			return null; // We return always null in this case
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Close all opened streams
	 */
	@Override
	public void close() throws IOException {
		for (RandomAccessFile file : openedRandomAccessFileList.values()) {
			file.close();
		}
	}

	@Override
	public void setCompleted(Long files, Long bytes) throws SevenZipException {
	}

	@Override
	public void setTotal(Long files, Long bytes) throws SevenZipException {
	}

}
