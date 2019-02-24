package net.filebot;

import static net.filebot.Logging.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sun.jna.Platform;

import net.filebot.platform.bsd.ExtAttrView;
import net.filebot.platform.mac.MacXattrView;
import net.filebot.util.DefaultXattrView;
import net.filebot.util.PlainFileXattrView;
import net.filebot.util.XattrView;

public class MetaAttributeView extends AbstractMap<String, String> {

	private final boolean FORCE_XATTR_STORE = System.getProperty("net.filebot.xattr.store") != null;

	private XattrView fs;

	public MetaAttributeView(File file) throws IOException {
		// resolve symlinks
		Path path = file.toPath().toRealPath();

		if (FORCE_XATTR_STORE) {
			fs = new PlainFileXattrView(path);
		} else if (Platform.isWindows() || Platform.isLinux()) {
			fs = new DefaultXattrView(path);
		} else if (Platform.isMac()) {
			fs = new MacXattrView(path);
		} else if (Platform.isFreeBSD() || Platform.isOpenBSD() || Platform.isNetBSD()) {
			fs = new ExtAttrView(path);
		} else {
			fs = new DefaultXattrView(path);
		}
	}

	@Override
	public String get(Object key) {
		return get(key.toString());
	}

	public String get(String key) {
		try {
			return fs.read(key);
		} catch (IOException e) {
			debug.warning(e::toString);
		}
		return null;
	}

	@Override
	public String put(String key, String value) {
		try {
			if (value == null || value.isEmpty()) {
				fs.delete(key);
			} else {
				fs.write(key, value);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return null; // since we don't know the old value
	}

	@Override
	public void clear() {
		try {
			for (String key : fs.list()) {
				fs.delete(key);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> list() throws IOException {
		return fs.list();
	}

	@Override
	public Set<Entry<String, String>> entrySet() {
		try {
			Set<Entry<String, String>> entries = new LinkedHashSet<Entry<String, String>>();
			for (String name : list()) {
				entries.add(new AttributeEntry(name));
			}
			return entries;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private class AttributeEntry implements Entry<String, String> {

		private final String name;

		public AttributeEntry(String name) {
			this.name = name;
		}

		@Override
		public String getKey() {
			return name;
		}

		@Override
		public String getValue() {
			return get(name);
		}

		@Override
		public String setValue(String value) {
			return put(name, value);
		}

		@Override
		public String toString() {
			return getKey() + "=" + getValue();
		}
	}

}
