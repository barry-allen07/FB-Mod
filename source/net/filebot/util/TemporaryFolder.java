package net.filebot.util;

import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TemporaryFolder {

	private static final Map<String, TemporaryFolder> folders = new HashMap<String, TemporaryFolder>();

	/**
	 * Get a {@link TemporaryFolder} instance for a given name. The actual directory will be created lazily (e.g. when a file is created). The name of the directory will start with the given name (lower-case) and contain a unique id, so multiple application instances may run at the same time without the risk of interference.
	 *
	 * @param name
	 *            case-insensitive name of a temporary folder (e.g. application name)
	 * @return temporary folder for this name
	 */
	public static TemporaryFolder getFolder(String name) {
		// make name case-insensitive
		name = name.toLowerCase();

		synchronized (folders) {
			TemporaryFolder folder = folders.get(name);

			if (folder == null) {
				File tmpdir = new File(System.getProperty("java.io.tmpdir"));
				String subdir = String.format("%s [%s]", name, UUID.randomUUID());

				folder = new TemporaryFolder(new File(tmpdir, subdir));
				folders.put(name, folder);
			}

			return folder;
		}
	}

	/**
	 * Delete all temporary folders on shutdown
	 */
	static {
		Runtime.getRuntime().addShutdownHook(new Thread("TemporaryFolder Cleanup") {

			@Override
			public void run() {
				synchronized (folders) {
					for (TemporaryFolder folder : folders.values()) {
						folder.delete();
					}
				}
			}
		});
	}

	private final File root;

	private TemporaryFolder(File root) {
		this.root = root;
	}

	/**
	 * Create an empty file in this temporary folder.
	 *
	 * @param name
	 *            name of the file
	 * @return newly created file
	 * @throws IOException
	 *             if an I/O error occurred
	 */
	public File createFile(String name) throws IOException {
		// if the directory does not exist it will be created
		File file = new File(getFolder(), name);
		file.createNewFile();

		return file;
	}

	/**
	 * Creates an empty file in this temporary folder, using the given prefix and suffix to generate its name.
	 *
	 * @param prefix
	 *            The prefix string to be used in generating the file's name; must be at least three characters long
	 * @param suffix
	 *            The suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp" will be used
	 * @return An abstract pathname denoting a newly-created empty file
	 * @throws IOException
	 *             If a file could not be created
	 * @see File#createTempFile(String, String)
	 */
	public File createFile(String prefix, String suffix) throws IOException {
		return File.createTempFile(prefix, suffix, getFolder());
	}

	public boolean deleteFile(String name) throws IOException {
		return new File(getFolder(), name).delete();
	}

	/**
	 * Retrieve the {@link File} object for this {@link TemporaryFolder}. The actual directory for the {@link TemporaryFolder} instance will be created by this method.
	 *
	 * @return the {@link File} object for this {@link TemporaryFolder}
	 */
	public File getFolder() throws IOException {
		FileUtilities.createFolders(root);
		return root;
	}

	public TemporaryFolder subFolder(String name) throws IOException {
		return new TemporaryFolder(new File(getFolder(), name));
	}

	public List<File> list(boolean recursive) {
		List<File> list = new ArrayList<File>();

		list(root, list, recursive);

		return list;
	}

	private void list(File file, List<File> list, boolean recursive) {
		if (file.isDirectory()) {
			for (File entry : getChildren(file)) {
				if (entry.isDirectory()) {
					if (recursive) {
						list(entry, list, recursive);
					}
				} else {
					list.add(entry);
				}
			}
		}
	}

	public void delete() {
		delete(root);
	}

	/**
	 * Delete files/folders recursively
	 *
	 * @param file
	 *            file/folder that will be deleted
	 */
	private void delete(File file) {
		if (file.isDirectory()) {
			for (File entry : getChildren(file)) {
				delete(entry);
			}
		}

		file.delete();
	}

}
