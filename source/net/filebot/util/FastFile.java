package net.filebot.util;

import static java.util.Arrays.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.BitSet;

public class FastFile extends File {

	private static final long UNDEFINED = -1;

	public static final int HIDDEN = 0;
	public static final int DIRECTORY = 1;
	public static final int FILE = 2;

	private BitSet stats;

	private String name;
	private long length = UNDEFINED;
	private long lastModified = UNDEFINED;

	private long totalSpace = UNDEFINED;
	private long freeSpace = UNDEFINED;

	private String[] list;
	private File[] listFiles;

	private File canonicalFile;
	private File parentFile;

	public FastFile(File file) {
		super(file.getPath());
	}

	public FastFile(File parentFile, String name) {
		super(parentFile, name);

		this.parentFile = parentFile;
		this.name = name;
	}

	public boolean stats(int bitIndex) {
		if (stats == null) {
			stats = new BitSet(3);
			stats.set(HIDDEN, super.isHidden());

			if (super.isFile()) {
				stats.set(FILE);
			} else if (super.isDirectory()) {
				stats.set(DIRECTORY);
			}
		}

		return stats.get(bitIndex);

	}

	@Override
	public boolean isDirectory() {
		return stats(DIRECTORY);
	}

	@Override
	public boolean isFile() {
		return stats(FILE);
	}

	@Override
	public boolean isHidden() {
		return stats(HIDDEN);
	}

	@Override
	public String getName() {
		return name != null ? name : (name = super.getName());
	}

	@Override
	public long length() {
		return length != UNDEFINED ? length : (length = super.length());
	}

	@Override
	public long lastModified() {
		return lastModified != UNDEFINED ? lastModified : (lastModified = super.lastModified());
	}

	@Override
	public File getCanonicalFile() throws IOException {
		return canonicalFile != null ? canonicalFile : (canonicalFile = get(super.getCanonicalFile()));
	}

	@Override
	public File getParentFile() {
		return parentFile != null ? parentFile : (parentFile = get(super.getParentFile()));
	}

	@Override
	public String[] list() {
		if (list != null) {
			return list;
		}

		String[] names = super.list();
		if (names == null) {
			names = new String[0];
		}

		return (list = names);
	}

	@Override
	public File[] listFiles() {
		if (listFiles != null) {
			return listFiles;
		}

		return (listFiles = stream(list()).map(s -> new FastFile(this, s)).toArray(File[]::new));
	}

	@Override
	public File[] listFiles(FileFilter filter) {
		return stream(listFiles()).filter(filter::accept).toArray(File[]::new);
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public boolean canRead() {
		return true;
	}

	@Override
	public boolean canWrite() {
		return false;
	}

	@Override
	public boolean canExecute() {
		return false;
	}

	@Override
	public long getTotalSpace() {
		return totalSpace != UNDEFINED ? totalSpace : (totalSpace = super.getTotalSpace());
	}

	@Override
	public long getUsableSpace() {
		return freeSpace != UNDEFINED ? freeSpace : (freeSpace = super.getUsableSpace());
	}

	@Override
	public long getFreeSpace() {
		return freeSpace != UNDEFINED ? freeSpace : (freeSpace = super.getUsableSpace());
	}

	@Override
	public boolean createNewFile() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean delete() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteOnExit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean mkdir() {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean mkdirs() {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean renameTo(File dest) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean setLastModified(long time) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean setReadOnly() {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setWritable(boolean writable, boolean ownerOnly) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean setWritable(boolean writable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean setReadable(boolean readable, boolean ownerOnly) {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setReadable(boolean readable) {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setExecutable(boolean executable, boolean ownerOnly) {
		throw new UnsupportedOperationException();

	}

	@Override
	public boolean setExecutable(boolean executable) {
		throw new UnsupportedOperationException();

	}

	public static FastFile get(File f) {
		return f == null ? null : new FastFile(f);
	}

}
