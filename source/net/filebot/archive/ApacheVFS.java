package net.filebot.archive;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.CacheStrategy;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelectInfo;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.cache.NullFilesCache;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;

import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;

public class ApacheVFS implements ArchiveExtractor, Closeable {

	private static final FileSelector ALL_FILES = new AllFileSelector();

	private final StandardFileSystemManager fsm;
	private final FileObject archive;

	public ApacheVFS(File file) throws Exception {
		if (!file.exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}

		fsm = new StandardFileSystemManager();
		fsm.setCacheStrategy(CacheStrategy.MANUAL);
		fsm.setFilesCache(new NullFilesCache());
		fsm.init();

		archive = fsm.createFileSystem(fsm.toFileObject(file));
	}

	@Override
	public List<FileInfo> listFiles() throws Exception {
		List<FileInfo> paths = new ArrayList<FileInfo>();
		for (FileObject it : archive.findFiles(ALL_FILES)) {
			if (it.getType() == FileType.FILE) {
				// ignore leading / slash
				paths.add(new SimpleFileInfo(it.getName().getPathDecoded().substring(1), it.getContent().getSize()));
			}
		}
		return paths;
	}

	@Override
	public void extract(File outputDir) throws Exception {
		extract(outputDir, null);
	}

	@Override
	public void extract(File outputDir, FileFilter filter) throws Exception {
		fsm.toFileObject(outputDir).copyFrom(archive, filter == null ? ALL_FILES : new FileFilterSelector(filter));
	}

	@Override
	public void close() throws IOException {
		archive.close();
		fsm.close();
	}

	private static class FileFilterSelector implements FileSelector {

		private final FileFilter filter;

		public FileFilterSelector(FileFilter filter) {
			this.filter = filter;
		}

		@Override
		public boolean traverseDescendents(FileSelectInfo it) throws Exception {
			return true;
		}

		@Override
		public boolean includeFile(FileSelectInfo it) throws Exception {
			// ignore leading / slash
			return filter.accept(new File(it.getFile().getName().getPathDecoded().substring(1)));
		}

	}

}
