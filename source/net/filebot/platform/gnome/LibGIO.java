
package net.filebot.platform.gnome;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

interface LibGIO extends Library {

	void g_type_init();

	Pointer g_vfs_get_default();

	Pointer g_vfs_get_file_for_uri(Pointer gvfs, String uri);

	Pointer g_file_get_path(Pointer gfile);

	void g_free(Pointer gpointer);

	void g_object_unref(Pointer gobject);

}
