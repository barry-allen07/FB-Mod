package net.filebot.archive;

import static java.util.Arrays.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.util.StringUtilities.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;

import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.util.SystemProperty;
import net.filebot.vfs.FileInfo;
import net.sf.sevenzipjbinding.ArchiveFormat;

public class Archive implements Closeable {

	public static Extractor getExtractor() {
		return SystemProperty.of("net.filebot.Archive.extractor", Extractor::valueOf, Extractor.SevenZipNativeBindings).get();
	}

	public static enum Extractor {

		SevenZipNativeBindings, SevenZipExecutable, ApacheVFS;

		public ArchiveExtractor newInstance(File archive) throws Exception {
			switch (this) {
			case SevenZipNativeBindings:
				return new SevenZipNativeBindings(archive);
			case SevenZipExecutable:
				return new SevenZipExecutable(archive);
			default:
				return new ApacheVFS(archive);
			}
		}

		public String[] getSupportedTypes() {
			switch (this) {
			case SevenZipNativeBindings:
			case SevenZipExecutable:
				return stream(ArchiveFormat.values()).map(ArchiveFormat::getMethodName).toArray(String[]::new);
			default:
				try {
					return VFS.getManager().getSchemes();
				} catch (FileSystemException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	public static Archive open(File archive) throws Exception {
		return new Archive(getExtractor().newInstance(archive));
	}

	private final ArchiveExtractor extractor;

	public Archive(ArchiveExtractor extractor) throws Exception {
		this.extractor = extractor;
	}

	public List<FileInfo> listFiles() throws Exception {
		return extractor.listFiles();
	}

	public void extract(File outputDir) throws Exception {
		extractor.extract(outputDir);
	}

	public void extract(File outputDir, FileFilter filter) throws Exception {
		extractor.extract(outputDir, filter);
	}

	@Override
	public void close() throws IOException {
		if (extractor instanceof Closeable) {
			((Closeable) extractor).close();
		}
	}

	public static String[] getArchiveTypes() {
		return Stream.of(ARCHIVE_FILES.extensions(), Extractor.SevenZipNativeBindings.getSupportedTypes()).flatMap(Stream::of).distinct().toArray(String[]::new);
	}

	private static final Pattern multiPartIndex = Pattern.compile("[.][0-9]{3}$");

	public static boolean hasMultiPartIndex(File file) {
		return multiPartIndex.matcher(file.getName()).find();
	}

	public static final FileFilter VOLUME_ONE_FILTER = new FileFilter() {

		private final Pattern volume = Pattern.compile("[.]r[0-9]+$|[.]part[0-9]+|[.][0-9]+$", Pattern.CASE_INSENSITIVE);
		private final FileFilter archives = new ExtensionFileFilter(getArchiveTypes());

		@Override
		public boolean accept(File path) {
			if (!archives.accept(path) && !hasMultiPartIndex(path)) {
				return false;
			}

			Matcher matcher = volume.matcher(path.getName());
			if (matcher.find()) {
				Integer i = matchInteger(matcher.group());
				if (i == null || i != 1) {
					return false;
				}
			}

			return true;
		}

	};

}
