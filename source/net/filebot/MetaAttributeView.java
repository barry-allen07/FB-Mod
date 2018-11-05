package net.filebot;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.Logging.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sun.jna.Platform;

import net.filebot.platform.mac.MacXattrView;

public class MetaAttributeView extends AbstractMap<String, String> {

	private final Object xattr;

	public MetaAttributeView(File file) throws IOException {
		// resolve symlinks
		Path path = file.toPath().toRealPath();

		// UserDefinedFileAttributeView (for Windows and Linux) OR our own xattr.h JNA wrapper via MacXattrView (for Mac) because UserDefinedFileAttributeView is not supported (Oracle Java 7/8)
		if (Platform.isMac()) {
			xattr = new MacXattrView(path);
		} else {
			xattr = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
		}

		// sanity check
		if (xattr == null) {
			throw new IOException("UserDefinedFileAttributeView is not supported");
		}
	}

	@Override
	public String get(Object key) {
		return get(key.toString());
	}

	public String get(String key) {
		try {
			if (xattr instanceof UserDefinedFileAttributeView) {
				UserDefinedFileAttributeView attributeView = (UserDefinedFileAttributeView) xattr;
				try {
					ByteBuffer buffer = ByteBuffer.allocate(attributeView.size(key));
					attributeView.read(key, buffer);
					buffer.flip();

					return UTF_8.decode(buffer).toString();
				} catch (FileSystemException e) {
					// attribute does not exist
					return null;
				}
			}

			if (xattr instanceof MacXattrView) {
				MacXattrView macXattr = (MacXattrView) xattr;
				return macXattr.read(key);
			}
		} catch (IOException e) {
			debug.warning(cause(e));
		}

		return null;
	}

	@Override
	public String put(String key, String value) {
		try {
			if (xattr instanceof UserDefinedFileAttributeView) {
				UserDefinedFileAttributeView attributeView = (UserDefinedFileAttributeView) xattr;
				if (value == null || value.isEmpty()) {
					attributeView.delete(key);
				} else {
					attributeView.write(key, UTF_8.encode(value));
				}
			}

			if (xattr instanceof MacXattrView) {
				MacXattrView macXattr = (MacXattrView) xattr;
				if (value == null || value.isEmpty()) {
					macXattr.delete(key);
				} else {
					macXattr.write(key, value);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return null; // since we don't know the old value
	}

	@Override
	public void clear() {
		try {
			for (String key : list()) {
				put(key, null);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> list() throws IOException {
		if (xattr instanceof UserDefinedFileAttributeView) {
			UserDefinedFileAttributeView attributeView = (UserDefinedFileAttributeView) xattr;
			return attributeView.list();
		}

		if (xattr instanceof MacXattrView) {
			MacXattrView macXattr = (MacXattrView) xattr;
			return macXattr.list();
		}

		return null;
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
