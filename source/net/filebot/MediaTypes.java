package net.filebot;

import static java.util.Collections.*;
import static net.filebot.util.RegularExpressions.*;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import net.filebot.util.FileUtilities.ExtensionFileFilter;

public class MediaTypes {

	private static Map<String, ExtensionFileFilter> types = getKnownMediaTypes();

	private static Map<String, ExtensionFileFilter> getKnownMediaTypes() {
		Map<String, ExtensionFileFilter> types = new LinkedHashMap<String, ExtensionFileFilter>(64);

		ResourceBundle bundle = ResourceBundle.getBundle(MediaTypes.class.getName());
		for (Enumeration<String> keys = bundle.getKeys(); keys.hasMoreElements();) {
			String type = keys.nextElement();
			String[] extensions = SPACE.split(bundle.getString(type));

			types.put(type, new ExtensionFileFilter(extensions));
		}

		return types;
	}

	public static void main(String[] args) {
		System.out.println(MediaTypes.types);
	}

	public static String getMediaType(String extension) {
		for (Entry<String, ExtensionFileFilter> it : types.entrySet()) {
			if (it.getValue().acceptExtension(extension)) {
				return it.getKey();
			}
		}
		return null;
	}

	public static ExtensionFileFilter getTypeFilter(String name) {
		return types.get(name);
	}

	public static ExtensionFileFilter getCategoryFilter(String category) {
		List<String> extensions = new ArrayList<String>();

		for (Entry<String, ExtensionFileFilter> it : types.entrySet()) {
			if (it.getKey().startsWith(category)) {
				addAll(extensions, it.getValue().extensions());
			}
		}

		return new ExtensionFileFilter(extensions);
	}

	public static final ExtensionFileFilter AUDIO_FILES = getCategoryFilter("audio");
	public static final ExtensionFileFilter VIDEO_FILES = getCategoryFilter("video");
	public static final ExtensionFileFilter SUBTITLE_FILES = getCategoryFilter("subtitle");
	public static final ExtensionFileFilter IMAGE_FILES = getCategoryFilter("image");
	public static final ExtensionFileFilter ARCHIVE_FILES = getCategoryFilter("archive");
	public static final ExtensionFileFilter VERIFICATION_FILES = getCategoryFilter("verification");

	public static final ExtensionFileFilter NFO_FILES = getTypeFilter("application/nfo");
	public static final ExtensionFileFilter LIST_FILES = getTypeFilter("application/list");
	public static final ExtensionFileFilter TORRENT_FILES = getTypeFilter("application/torrent");

	public static final ExtensionFileFilter LICENSE_FILES = getTypeFilter("application/filebot-license");

}
