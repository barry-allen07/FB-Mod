package net.filebot.media;

import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;

import net.filebot.ResourceManager;
import net.filebot.web.Datasource;

public enum LocalDatasource implements Datasource {

	XATTR, EXIF, FILE;

	@Override
	public String getIdentifier() {
		switch (this) {
		case XATTR:
			return "xattr";
		case EXIF:
			return "exif";
		default:
			return "file";
		}
	}

	@Override
	public String getName() {
		switch (this) {
		case XATTR:
			return "Extended Attributes";
		case EXIF:
			return "Exif Metadata";
		default:
			return "Plain File";
		}
	}

	@Override
	public Icon getIcon() {
		switch (this) {
		case XATTR:
			return ResourceManager.getIcon("search.xattr");
		case EXIF:
			return ResourceManager.getIcon("search.exif");
		default:
			return ResourceManager.getIcon("search.generic");
		}
	}

	public Map<File, Object> match(Collection<File> files, boolean strict) {
		switch (this) {
		case XATTR:
			Map<File, Object> xattrMap = new LinkedHashMap<File, Object>(files.size());
			for (File f : files) {
				Object object = xattr.getMetaInfo(f);
				if (object != null) {
					xattrMap.put(f, object);
				} else if (!strict) {
					xattrMap.put(f, f);
				}
			}
			return xattrMap;
		case EXIF:
			Map<File, Object> exifMap = new LinkedHashMap<File, Object>(files.size());
			for (File f : filter(files, ImageMetadata.SUPPORTED_FILE_TYPES)) {
				try {
					ImageMetadata metadata = new ImageMetadata(f);
					if (metadata.getDateTaken().isPresent()) {
						exifMap.put(f, new PhotoFile(f, metadata)); // photo mode is the same as generic file mode (but only select photo files)
					} else if (!strict) {
						exifMap.put(f, f);
					}
				} catch (Exception e) {
					debug.warning(format("%s [%s]", e, f));
				}
			}
			return exifMap;
		default:
			return files.stream().collect(toMap(f -> f, f -> f, (a, b) -> a, LinkedHashMap::new));
		}
	}

	// enable xattr regardless of -DuseExtendedFileAttributes system properties
	private static final XattrMetaInfo xattr = new XattrMetaInfo(true, false);

	public static class PhotoFile extends File {

		private final ImageMetadata metadata;

		public PhotoFile(File file, ImageMetadata metadata) {
			super(file.getPath());
			this.metadata = metadata;
		}

		public ImageMetadata getMetadata() {
			return metadata;
		}
	}

}
