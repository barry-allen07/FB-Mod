
package net.filebot.platform.gnome;

import java.io.File;
import java.net.URI;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class NativeGVFS implements GVFS {

	private static final LibGIO lib_gio = (LibGIO) Native.loadLibrary("gio-2.0", LibGIO.class);
	private static final Pointer gvfs = lib_gio.g_vfs_get_default();

	public File getPathForURI(URI uri) {
		Pointer gfile = lib_gio.g_vfs_get_file_for_uri(gvfs, uri.toString());
		Pointer chars = lib_gio.g_file_get_path(gfile);

		try {
			if (chars != null) {
				return new File(chars.getString(0));
			}

			throw new IllegalArgumentException("Failed to locate local path: " + uri);
		} finally {
			lib_gio.g_object_unref(gfile);
			lib_gio.g_free(chars);
		}
	}

	@Override
	public String toString() {
		return String.format("%s [%s]", getClass().getSimpleName(), lib_gio);
	}

}
