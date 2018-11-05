package net.filebot.media;

import static java.util.Arrays.*;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.file.FileSystemDirectory;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

public class ImageMetadata {

	private final Metadata metadata;

	public ImageMetadata(File file) throws ImageProcessingException, IOException {
		if (!SUPPORTED_FILE_TYPES.accept(file)) {
			throw new IllegalArgumentException("Image type not supported: " + file);
		}

		metadata = ImageMetadataReader.readMetadata(file);
	}

	public Map<String, String> snapshot() {
		return snapshot(Tag::getTagName);
	}

	public Map<String, String> snapshot(Function<Tag, String> key) {
		return snapshot(key, d -> Stream.of("JPEG", "JFIF", "Interoperability", "Huffman", "File").noneMatch(d.getName()::equals));
	}

	public Map<String, String> snapshot(Function<Tag, String> key, Predicate<Directory> accept) {
		Map<String, String> values = new LinkedHashMap<String, String>();

		for (Directory directory : metadata.getDirectories()) {
			if (accept.test(directory)) {
				for (Tag tag : directory.getTags()) {
					String v = tag.getDescription();
					if (v != null && v.length() > 0) {
						values.put(key.apply(tag), v);
					}
				}
			}
		}

		return values;
	}

	public Optional<String> getName() {
		return extract(m -> m.getFirstDirectoryOfType(FileSystemDirectory.class)).map(d -> d.getString(FileSystemDirectory.TAG_FILE_NAME));
	}

	public Optional<ZonedDateTime> getDateTaken() {
		return extract(m -> m.getFirstDirectoryOfType(ExifIFD0Directory.class)).map(d -> d.getDate(ExifSubIFDDirectory.TAG_DATETIME)).map(d -> {
			return d.toInstant().atZone(ZoneOffset.UTC);
		});
	}

	public Optional<Map<CameraProperty, String>> getCameraModel() {
		return extract(m -> m.getFirstDirectoryOfType(ExifIFD0Directory.class)).map(d -> {
			String maker = d.getDescription(ExifIFD0Directory.TAG_MAKE);
			String model = d.getDescription(ExifIFD0Directory.TAG_MODEL);

			Map<CameraProperty, String> camera = new EnumMap<CameraProperty, String>(CameraProperty.class);
			if (maker != null) {
				camera.put(CameraProperty.maker, maker);
			}
			if (model != null) {
				camera.put(CameraProperty.model, model);
			}

			return camera;
		}).filter(m -> !m.isEmpty());
	}

	public enum CameraProperty {
		maker, model;
	}

	public Optional<Map<AddressComponent, String>> getLocationTaken() {
		return extract(m -> m.getFirstDirectoryOfType(GpsDirectory.class)).map(GpsDirectory::getGeoLocation).map(this::locate);
	}

	protected Map<AddressComponent, String> locate(GeoLocation location) {
		try {
			// e.g. https://maps.googleapis.com/maps/api/geocode/json?latlng=40.7470444,-073.9411611
			Cache cache = Cache.getCache("geocode", CacheType.Persistent);

			Object json = cache.json(location.getLatitude() + "," + location.getLongitude(), p -> new URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=" + p)).get();

			Map<AddressComponent, String> address = new EnumMap<AddressComponent, String>(AddressComponent.class);

			streamJsonObjects(json, "results").limit(1).forEach(r -> {
				streamJsonObjects(r, "address_components").forEach(a -> {
					String name = getString(a, "long_name");
					if (name != null) {
						for (Object type : getArray(a, "types")) {
							stream(AddressComponent.values()).filter(c -> c.name().equals(type)).findFirst().ifPresent(c -> {
								address.putIfAbsent(c, name);
							});
						}
					}
				});
			});

			return address;
		} catch (Exception e) {
			debug.warning(e::toString);
		}

		return null;
	}

	public enum AddressComponent {
		country, administrative_area_level_1, administrative_area_level_2, administrative_area_level_3, administrative_area_level_4, sublocality, neighborhood, route;
	}

	public <T> Optional<T> extract(Function<Metadata, T> extract) {
		try {
			return Optional.ofNullable(extract.apply(metadata));
		} catch (Exception e) {
			debug.finest(format("Failed to extract image metadata: %s", e));
		}
		return Optional.empty();
	}

	public static final FileFilter SUPPORTED_FILE_TYPES = new ExtensionFileFilter("jpg", "jpeg", "png", "webp", "gif", "ico", "bmp", "tif", "tiff", "psd", "pcx", "raw", "crw", "cr2", "nef", "orf", "raf", "rw2", "rwl", "srw", "arw", "dng", "x3f", "mov", "mp4", "m4v", "3g2", "3gp", "3gp");

}
