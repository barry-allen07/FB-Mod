package net.filebot.archive;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;

public class SevenZipNativeBindings implements ArchiveExtractor, Closeable {

	private IInArchive inArchive;
	private ArchiveOpenVolumeCallback openVolume;

	public SevenZipNativeBindings(File file) throws Exception {
		// initialize 7-Zip-JBinding
		if (!file.exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}

		openVolume = new ArchiveOpenVolumeCallback();

		if (!Archive.hasMultiPartIndex(file)) {
			// single volume archives and multi-volume rar archives
			inArchive = SevenZipLoader.open(openVolume.getStream(file.getAbsolutePath()), openVolume);
		} else {
			// raw multi-volume archives
			inArchive = SevenZipLoader.open(new net.sf.sevenzipjbinding.impl.VolumedArchiveInStream(file.getAbsolutePath(), openVolume), null);
		}
	}

	public int itemCount() throws SevenZipException {
		return inArchive.getNumberOfItems();
	}

	public Map<PropID, Object> getItem(int index) throws SevenZipException {
		Map<PropID, Object> item = new EnumMap<PropID, Object>(PropID.class);

		for (PropID prop : PropID.values()) {
			Object value = inArchive.getProperty(index, prop);
			if (value != null) {
				item.put(prop, value);
			}
		}

		return item;
	}

	@Override
	public List<FileInfo> listFiles() throws SevenZipException {
		List<FileInfo> paths = new ArrayList<FileInfo>();

		for (int i = 0; i < inArchive.getNumberOfItems(); i++) {
			boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
			if (!isFolder) {
				String path = (String) inArchive.getProperty(i, PropID.PATH);
				Long length = (Long) inArchive.getProperty(i, PropID.SIZE);
				if (path != null) {
					paths.add(new SimpleFileInfo(path, length != null ? length : -1));
				}
			}
		}

		return paths;
	}

	@Override
	public void extract(File outputDir) throws Exception {
		extract(new FileMapper(outputDir));
	}

	@Override
	public void extract(File outputDir, FileFilter filter) throws Exception {
		extract(new FileMapper(outputDir), filter);
	}

	public void extract(ExtractOutProvider outputMapper) throws SevenZipException {
		inArchive.extract(null, false, new ExtractCallback(inArchive, outputMapper));
	}

	public void extract(ExtractOutProvider outputMapper, FileFilter filter) throws SevenZipException {
		List<Integer> selection = new ArrayList<Integer>();

		for (int i = 0; i < inArchive.getNumberOfItems(); i++) {
			boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
			if (!isFolder) {
				String path = (String) inArchive.getProperty(i, PropID.PATH);
				if (path != null && filter.accept(new File(path))) {
					selection.add(i);
				}
			}
		}

		int[] indices = new int[selection.size()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = selection.get(i);
		}
		inArchive.extract(indices, false, new ExtractCallback(inArchive, outputMapper));
	}

	@Override
	public void close() throws IOException {
		try {
			inArchive.close();
		} catch (SevenZipException e) {
			throw new IOException(e);
		} finally {
			openVolume.close();
		}
	}

}
