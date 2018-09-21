package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static net.filebot.Logging.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

public final class FileUtilities {

	public static File moveRename(File source, File destination) throws IOException {
		// resolve destination
		destination = resolveDestination(source, destination);

		// do nothing if source and destination path is the same
		if (equalsCaseSensitive(source, destination)) {
			return destination;
		}

		if (source.isDirectory()) {
			// move folder
			FileUtils.moveDirectory(source, destination);
			return destination;
		}

		// on Windows, use ATOMIC_MOVE which allows us to rename files even if only lower/upper-case changes (without ATOMIC_MOVE the operation would be ignored)
		// but ATOMIC_MOVE can only work for files on the same drive, if that is not the case there is no point trying move with ATOMIC_MOVE
		if (source.equals(destination)) {
			try {
				return Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE).toFile();
			} catch (AtomicMoveNotSupportedException e) {
				debug.warning(e::toString);
			}
		}

		// Linux and Mac OS X
		return Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile();
	}

	public static File copyAs(File source, File destination) throws IOException {
		// resolve destination
		destination = resolveDestination(source, destination);

		if (source.isDirectory()) {
			// copy folder
			FileUtils.copyDirectory(source, destination);
			return destination;
		}

		// copy file
		return Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile();
	}

	public static File resolve(File source, File destination) {
		// resolve destination
		if (!destination.isAbsolute()) {
			// same folder, different name
			destination = new File(source.getParentFile(), destination.getPath());
		}
		return destination;
	}

	public static File resolveDestination(File source, File destination) throws IOException {
		// resolve destination
		destination = resolve(source, destination);

		// create parent folder if necessary and make sure that the folder structure is created, and throw exception if the folder structure can't be created
		Path parentFolder = destination.toPath().getParent();
		if (Files.notExists(parentFolder, LinkOption.NOFOLLOW_LINKS)) {
			Files.createDirectories(parentFolder);
		}

		return destination;
	}

	public static File createRelativeSymlink(File link, File target, boolean relativize) throws IOException {
		if (relativize) {
			// make sure we're working with the correct full path of the file or link
			try {
				target = link.toPath().getParent().toRealPath(LinkOption.NOFOLLOW_LINKS).relativize(target.toPath()).toFile();
			} catch (Throwable e) {
				log.warning(cause("Unable to relativize link target", e));
			}
		}

		// create symlink via NIO.2
		return Files.createSymbolicLink(link.toPath(), target.toPath()).toFile();
	}

	public static File createHardLinkStructure(File link, File target) throws IOException {
		if (target.isFile()) {
			return Files.createLink(link.toPath(), target.toPath()).toFile();
		}

		// if the target is a directory, recreate the structure and hardlink each file item
		final Path source = target.getCanonicalFile().toPath();
		final Path destination = link.getCanonicalFile().toPath();

		Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), FILE_WALK_MAX_DEPTH, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path linkFile = destination.resolve(source.relativize(file));
				Files.createDirectories(linkFile.getParent());
				Files.createLink(linkFile, file);
				return FileVisitResult.CONTINUE;
			}
		});

		return destination.toFile();
	}

	public static boolean existsNoFollowLinks(File file) {
		return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	public static void delete(File file) throws IOException {
		if (file.isDirectory()) {
			Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(Path f, BasicFileAttributes attr) throws IOException {
					Files.delete(f);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path f, IOException e) throws IOException {
					Files.delete(f);
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			Files.delete(file.toPath());
		}
	}

	public static void createFolders(File folder) throws IOException {
		Files.createDirectories(folder.toPath());
	}

	private static final String WIN_THUMBNAIL_STORE = "Thumbs.db";
	private static final String MAC_THUMBNAIL_STORE = ".DS_Store";

	public static boolean isThumbnailStore(File file) {
		return MAC_THUMBNAIL_STORE.equals(file.getName()) || WIN_THUMBNAIL_STORE.equalsIgnoreCase(file.getName());
	}

	public static byte[] readFile(File file) throws IOException {
		return Files.readAllBytes(file.toPath());
	}

	public static String readTextFile(File file) throws IOException {
		long size = file.length();

		// ignore absurdly large text files that might cause OutOfMemoryError issues
		if (size > ONE_GIGABYTE) {
			throw new IOException(String.format("Text file is too large: %s (%s)", file, formatSize(size)));
		}

		byte[] bytes = readFile(file);

		BOM bom = BOM.detect(bytes); // check BOM

		if (bom != null) {
			return new String(bytes, bom.size(), bytes.length - bom.size(), bom.getCharset());
		} else {
			return new String(bytes, UTF_8);
		}
	}

	public static List<String> readLines(File file) throws IOException {
		return asList(NEWLINE.split(readTextFile(file)));
	}

	public static File writeFile(ByteBuffer data, File destination) throws IOException {
		try (FileChannel channel = FileChannel.open(destination.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			channel.write(data);
		}
		return destination;
	}

	public static File writeFile(byte[] data, File destination) throws IOException {
		return Files.write(destination.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).toFile();
	}

	public static Reader createTextReader(InputStream in, boolean guess, Charset declaredEncoding) throws IOException {
		byte head[] = new byte[BOM.SIZE];
		in.mark(head.length);
		in.read(head);
		in.reset(); // rewind

		// check BOM
		BOM bom = BOM.detect(head);

		if (bom != null) {
			in.skip(bom.size()); // skip BOM
			return new InputStreamReader(in, bom.getCharset());
		}

		// auto-detect character encoding
		if (guess) {
			CharsetDetector detector = new CharsetDetector();
			detector.setDeclaredEncoding(declaredEncoding.name());
			detector.setText(in);
			CharsetMatch match = detector.detect();
			if (match != null) {
				Reader reader = match.getReader();

				// reader may be null if detected character encoding is not supported
				if (reader != null) {
					return reader;
				}

				// ISO-8859-8-I is not supported, but ISO-8859-8 uses the same code points so we can use that instead
				switch (match.getName()) {
				case "ISO-8859-8-I":
					return new InputStreamReader(in, Charset.forName("ISO-8859-8"));
				default:
					debug.warning("Unsupported charset: " + match.getName());
				}
			}
		}

		// default to declared encoding
		return new InputStreamReader(in, declaredEncoding);
	}

	public static Reader createTextReader(File file) throws IOException {
		return createTextReader(new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE), true, UTF_8);
	}

	public static boolean equalsCaseSensitive(File a, File b) {
		return a.getPath().equals(b.getPath());
	}

	public static boolean equalsFileContent(File a, File b) {
		// must have the same file size
		if (a.length() != b.length()) {
			return false;
		}

		// must not be a folder
		if (a.isDirectory() || b.isDirectory()) {
			return false;
		}

		// must be equal byte by byte
		try {
			return FileUtils.contentEquals(a, b);
		} catch (Exception e) {
			log.warning(cause(e));
		}

		return false;
	}

	/**
	 * Pattern used for matching file extensions.
	 *
	 * e.g. "file.txt" -> match "txt", ".hidden" -> no match
	 */
	public static final Pattern EXTENSION = Pattern.compile("(?<=.[.])[\\p{Alnum}-]+$");
	public static final String UNC_PREFIX = "\\\\";

	public static String getExtension(File file) {
		if (file.isDirectory())
			return null;

		return getExtension(file.getName());
	}

	public static String getExtension(String name) {
		Matcher matcher = EXTENSION.matcher(name);

		if (matcher.find()) {
			// extension without leading '.'
			return matcher.group();
		}

		// no extension
		return null;
	}

	public static boolean hasExtension(File file, String... extensions) {
		// avoid native call for speed, if possible
		return hasExtension(file.getName(), extensions) && !file.isDirectory();
	}

	public static boolean hasExtension(String filename, String... extensions) {
		for (String it : extensions) {
			if (filename.length() - it.length() >= 2 && filename.charAt(filename.length() - it.length() - 1) == '.') {
				String tail = filename.substring(filename.length() - it.length(), filename.length());
				if (tail.equalsIgnoreCase(it)) {
					return true;
				}
			}
		}

		return false;
	}

	public static String getNameWithoutExtension(String name) {
		Matcher matcher = EXTENSION.matcher(name);

		if (matcher.find()) {
			return name.substring(0, matcher.start() - 1);
		}

		// no extension, return given name
		return name;
	}

	public static String getName(File file) {
		if (file == null) {
			return null;
		}

		// directory || root drive || network share
		if (file.isDirectory()) {
			return getFolderName(file);
		}

		return getNameWithoutExtension(file.getName());
	}

	public static String getFolderName(File file) {
		if (UNC_PREFIX.equals(file.getParent())) {
			return file.toString();
		}

		String name = file.getName();
		if (name.length() > 0) {
			return name;
		}

		// file might be a drive (only has a path, but no name)
		return replacePathSeparators(file.toString(), "");
	}

	public static boolean isDerived(File derivate, File prime) {
		String n = getName(prime).toLowerCase();
		String s = getName(derivate).toLowerCase();

		if (s.startsWith(n)) {
			return s.length() == n.length() || !Character.isLetterOrDigit(s.charAt(n.length())); // e.g. x.z is not considered derived from xy.z
		}
		return false;
	}

	public static boolean isDerivedByExtension(File derivate, File prime) {
		return isDerivedByExtension(getName(derivate), prime);
	}

	public static boolean isDerivedByExtension(String derivate, File prime) {
		String base = getName(prime).trim().toLowerCase();
		derivate = derivate.trim().toLowerCase();

		if (derivate.equals(base))
			return true;

		while (derivate.length() > base.length() && getExtension(derivate) != null) {
			derivate = getNameWithoutExtension(derivate);

			if (derivate.equals(base))
				return true;
		}

		return false;
	}

	public static boolean containsOnly(Collection<File> files, FileFilter filter) {
		if (files.isEmpty()) {
			return false;
		}
		for (File file : files) {
			if (!filter.accept(file))
				return false;
		}
		return true;
	}

	public static List<File> sortByUniquePath(Collection<File> files) {
		TreeSet<File> sortedSet = new TreeSet<File>(CASE_INSENSITIVE_PATH_ORDER); // sort by unique lower-case paths
		sortedSet.addAll(files);
		return new ArrayList<File>(sortedSet);
	}

	public static List<File> filter(Iterable<File> files, FileFilter... filters) {
		List<File> accepted = new ArrayList<File>();

		for (File file : files) {
			for (FileFilter filter : filters) {
				if (filter.accept(file)) {
					accepted.add(file);
					break;
				}
			}
		}

		return accepted;
	}

	public static FileFilter not(FileFilter filter) {
		return f -> !filter.accept(f);
	}

	public static FileFilter filter(FileFilter... filters) {
		return f -> stream(filters).anyMatch(it -> it.accept(f));
	}

	public static List<File> listPath(File file) {
		return listPathTail(file, Integer.MAX_VALUE, false);
	}

	public static List<File> listPathTail(File file, int tailSize, boolean reverse) {
		LinkedList<File> nodes = new LinkedList<File>();

		File node = file;
		for (int i = 0; node != null && i < tailSize && !UNC_PREFIX.equals(node.toString()); i++, node = node.getParentFile()) {
			if (reverse) {
				nodes.addLast(node);
			} else {
				nodes.addFirst(node);
			}
		}

		return nodes;
	}

	public static File getRelativePathTail(File file, int tailSize) {
		File f = null;
		for (File it : listPathTail(file, tailSize, false)) {
			if (it.getParentFile() != null) {
				f = new File(f, it.getName());
			}
		}
		return f;
	}

	public static List<File> getFileSystemRoots() {
		File[] roots = File.listRoots();

		// roots array may be null if folder permissions do not allow listing of files
		if (roots == null) {
			roots = new File[0];
		}

		return asList(roots);
	}

	public static List<File> getChildren(File folder) {
		return getChildren(folder, null, null);
	}

	public static List<File> getChildren(File folder, FileFilter filter) {
		return getChildren(folder, filter, null);
	}

	public static List<File> getChildren(File folder, FileFilter filter, Comparator<File> order) {
		File[] files = filter == null ? folder.listFiles() : folder.listFiles(filter);

		// children array may be null if folder permissions do not allow listing of files
		if (files == null) {
			return emptyList();
		}

		if (order != null) {
			sort(files, order);
		}

		return asList(files);
	}

	public static final int FILE_WALK_MAX_DEPTH = 32;

	public static List<File> listFiles(File folder, FileFilter filter) {
		return listFiles(new File[] { folder }, FILE_WALK_MAX_DEPTH, filter, null);
	}

	public static List<File> listFiles(File folder, FileFilter filter, Comparator<File> order) {
		return listFiles(new File[] { folder }, FILE_WALK_MAX_DEPTH, filter, order);
	}

	public static List<File> listFiles(Collection<File> folders, FileFilter filter, Comparator<File> order) {
		return listFiles(folders.toArray(new File[0]), FILE_WALK_MAX_DEPTH, filter, order);
	}

	public static List<File> listFiles(File[] files, int depth, FileFilter filter, Comparator<File> order) {
		List<File> sink = new ArrayList<File>();

		// traverse file tree recursively
		streamFiles(files, FOLDERS, order).forEach(f -> listFiles(f, sink, depth, filter, order));

		// add selected files in preferred order
		streamFiles(files, filter, order).forEach(sink::add);

		return sink;
	}

	private static void listFiles(File folder, List<File> sink, int depth, FileFilter filter, Comparator<File> order) {
		if (depth < 0) {
			return;
		}

		// children array may be null if folder permissions do not allow listing of files
		File[] files = folder.listFiles(NOT_HIDDEN);

		// traverse file tree recursively
		streamFiles(files, FOLDERS, order).forEach(f -> listFiles(f, sink, depth - 1, filter, order));

		// add selected files in preferred order
		streamFiles(files, filter, order).forEach(sink::add);
	}

	private static Stream<File> streamFiles(File[] files, FileFilter filter, Comparator<File> order) {
		if (files == null || files.length == 0) {
			return Stream.empty();
		}

		if (order == null) {
			return stream(files).filter(filter::accept);
		} else {
			return stream(files).filter(filter::accept).sorted(order);
		}
	}

	public static SortedMap<File, List<File>> mapByFolder(Iterable<File> files) {
		SortedMap<File, List<File>> map = new TreeMap<File, List<File>>();

		for (File file : files) {
			File key = file.getParentFile();
			if (key == null) {
				throw new IllegalArgumentException("Parent is null: " + file);
			}

			List<File> valueList = map.get(key);
			if (valueList == null) {
				valueList = new ArrayList<File>();
				map.put(key, valueList);
			}

			valueList.add(file);
		}

		return map;
	}

	public static Map<String, List<File>> mapByExtension(Iterable<File> files) {
		Map<String, List<File>> map = new HashMap<String, List<File>>();

		for (File file : files) {
			String key = getExtension(file);
			if (key != null) {
				key = key.toLowerCase();
			}

			List<File> valueList = map.get(key);
			if (valueList == null) {
				valueList = new ArrayList<File>();
				map.put(key, valueList);
			}

			valueList.add(file);
		}

		return map;
	}

	/**
	 * Invalid file name characters: \, /, :, *, ?, ", <, >, |, \r, \n and excessive characters
	 */
	public static final Pattern ILLEGAL_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|\\r\\n]|\\p{Cntrl}|\\s+$|(?<=[^.])[.]+$|(?<=.{250})(.+)(?=[.]\\p{Alnum}{3}$)");

	/**
	 * Strip file name of invalid characters
	 *
	 * @param filename original filename
	 * @return valid file name stripped of invalid characters
	 */
	public static String validateFileName(CharSequence filename) {
		// strip invalid characters from file name
		return SPACE.matcher(ILLEGAL_CHARACTERS.matcher(filename).replaceAll("")).replaceAll(" ").trim();
	}

	public static boolean isInvalidFileName(CharSequence filename) {
		// check if file name contains any illegal characters
		return ILLEGAL_CHARACTERS.matcher(filename).find();
	}

	public static File validateFileName(File file) {
		// windows drives (e.g. c:, d:, etc.) are never invalid because name will be an empty string
		if (!isInvalidFileName(file.getName()))
			return file;

		// validate file name only
		return new File(file.getParentFile(), validateFileName(file.getName()));
	}

	public static File validateFilePath(File path) {
		Iterator<File> nodes = listPath(path).iterator();

		// initialize with root node, keep original root object if possible (so we don't loose the drive on windows)
		File validatedPath = validateFileName(nodes.next());

		// validate the rest of the path
		while (nodes.hasNext()) {
			validatedPath = new File(validatedPath, validateFileName(nodes.next().getName()));
		}

		return validatedPath;
	}

	public static boolean isInvalidFilePath(File path) {
		// check if file name contains any illegal characters
		for (File node = path; node != null; node = node.getParentFile()) {
			if (isInvalidFileName(node.getName()))
				return true;
		}

		return false;
	}

	public static String normalizePathSeparators(String path) {
		// special handling for UNC paths (e.g. \\server\share\path)
		if (path.startsWith(UNC_PREFIX)) {
			return UNC_PREFIX + normalizePathSeparators(path.substring(UNC_PREFIX.length()));
		}

		return replacePathSeparators(path, "/");
	}

	public static String replacePathSeparators(CharSequence path) {
		return replacePathSeparators(path, " ");
	}

	public static String replacePathSeparators(CharSequence path, String replacement) {
		return SLASH.matcher(path).replaceAll(replacement);
	}

	public static String md5(String string) {
		return md5(StandardCharsets.UTF_8.encode(string));
	}

	public static String md5(byte[] data) {
		return md5(ByteBuffer.wrap(data));
	}

	public static String md5(ByteBuffer data) {
		try {
			MessageDigest hash = MessageDigest.getInstance("MD5");
			hash.update(data);
			return String.format("%032x", new BigInteger(1, hash.digest())); // as hex string
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<File> asFileList(Object... paths) {
		List<File> files = new ArrayList<File>(paths.length);
		for (Object it : paths) {
			if (it instanceof CharSequence) {
				files.add(new File(it.toString()));
			} else if (it instanceof File) {
				files.add((File) it);
			} else if (it instanceof Path) {
				files.add(((Path) it).toFile());
			} else if (it instanceof Collection<?>) {
				files.addAll(asFileList(((Collection<?>) it).toArray())); // flatten object structure
			}
		}
		return files;
	}

	public static final int BUFFER_SIZE = 64 * 1024;

	public static final long ONE_KILOBYTE = 1000;
	public static final long ONE_MEGABYTE = 1000 * ONE_KILOBYTE;
	public static final long ONE_GIGABYTE = 1000 * ONE_MEGABYTE;

	public static String formatSize(long size) {
		if (size >= 100 * ONE_GIGABYTE)
			return String.format("%,d GB", size / ONE_GIGABYTE);
		if (size >= 10 * ONE_GIGABYTE)
			return String.format("%.1f GB", (double) size / ONE_GIGABYTE);
		if (size >= ONE_GIGABYTE)
			return String.format("%.2f GB", (double) size / ONE_GIGABYTE);
		if (size >= 10 * ONE_MEGABYTE)
			return String.format("%,d MB", size / ONE_MEGABYTE);
		if (size >= ONE_MEGABYTE)
			return String.format("%.1f MB", (double) size / ONE_MEGABYTE);
		if (size >= ONE_KILOBYTE)
			return String.format("%,d KB", size / ONE_KILOBYTE);

		return String.format("%,d bytes", size);
	}

	public static final FileFilter FOLDERS = File::isDirectory;

	public static final FileFilter FILES = File::isFile;

	public static final FileFilter NOT_HIDDEN = not(File::isHidden);

	public static final FileFilter TEMPORARY = new FileFilter() {

		private final String tmpdir = System.getProperty("java.io.tmpdir");

		@Override
		public boolean accept(File file) {
			return file.getPath().startsWith(tmpdir);
		}
	};

	public static class ParentFilter implements FileFilter {

		private final File folder;

		public ParentFilter(File folder) {
			this.folder = folder;
		}

		@Override
		public boolean accept(File file) {
			return listPath(file).contains(folder);
		}
	}

	public static class ExtensionFileFilter implements FileFilter, FilenameFilter {

		public static final List<String> WILDCARD = singletonList("*");

		private final String[] extensions;

		public ExtensionFileFilter(String... extensions) {
			this.extensions = extensions.clone();
		}

		public ExtensionFileFilter(Collection<String> extensions) {
			this.extensions = extensions.toArray(new String[0]);
		}

		@Override
		public boolean accept(File dir, String name) {
			return accept(name);
		}

		@Override
		public boolean accept(File file) {
			return accept(file.getName());
		}

		public boolean accept(String name) {
			return acceptAny() || hasExtension(name, extensions);
		}

		public boolean acceptAny() {
			return extensions.length == 1 && WILDCARD.get(0).equals(extensions[0]);
		}

		public boolean acceptExtension(String extension) {
			if (acceptAny()) {
				return true;
			}

			for (String other : extensions) {
				if (other.equalsIgnoreCase(extension)) {
					return true;
				}
			}

			return false;
		}

		public String extension() {
			return extensions[0];
		}

		public String[] extensions() {
			return extensions.clone();
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			for (String it : extensions) {
				if (s.length() > 0) {
					s.append(", ");
				}
				s.append("*.").append(it);
			}
			return s.toString();
		}

		public static ExtensionFileFilter union(ExtensionFileFilter... filters) {
			List<String> extensions = new ArrayList<String>();
			for (ExtensionFileFilter it : filters) {
				if (!it.acceptAny()) {
					addAll(extensions, it.extensions());
				}
			}
			return new ExtensionFileFilter(extensions);
		}
	}

	public static class RegexFileFilter implements FileFilter, FilenameFilter {

		private final Pattern pattern;

		public RegexFileFilter(Pattern pattern) {
			this.pattern = pattern;
		}

		@Override
		public boolean accept(File dir, String name) {
			return pattern.matcher(name).matches();
		}

		@Override
		public boolean accept(File file) {
			return accept(file.getParentFile(), file.getName());
		}
	}

	public static final Comparator<File> CASE_INSENSITIVE_PATH_ORDER = comparing(File::getPath, String.CASE_INSENSITIVE_ORDER);

	public static final Comparator<File> HUMAN_NAME_ORDER = comparing(File::getName, new AlphanumComparator(Locale.ENGLISH));

	/**
	 * Dummy constructor to prevent instantiation.
	 */
	private FileUtilities() {
		throw new UnsupportedOperationException();
	}

}
