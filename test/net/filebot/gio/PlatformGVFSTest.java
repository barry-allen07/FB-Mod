package net.filebot.gio;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;

import org.junit.Test;

import net.filebot.platform.gnome.GVFS;
import net.filebot.platform.gnome.PlatformGVFS;

public class PlatformGVFSTest {

	GVFS gvfs = new PlatformGVFS(new File("gvfs"));

	@Test
	public void smb() throws Exception {
		assertEquals("gvfs/smb-share:server=10.0.1.5,share=data/Movies/Avatar.mp4", gvfs.getPathForURI(new URI("smb://10.0.1.5/data/Movies/Avatar.mp4")).getPath());
		assertEquals("gvfs/smb-share:server=192.168.0.1,share=test/a file with spaces.txt", gvfs.getPathForURI(new URI("smb://192.168.0.1/test/a%20file%20with%20spaces.txt")).getPath());
	}

	@Test
	public void afp() throws Exception {
		assertEquals("gvfs/afp-volume:host=10.0.1.5,user=reinhard,volume=data/Movies/Avatar.mp4", gvfs.getPathForURI(new URI("afp://reinhard@10.0.1.5/data/Movies/Avatar.mp4")).getPath());
	}

	@Test
	public void sftp() throws Exception {
		assertEquals("gvfs/sftp:host=myserver.org,user=nico/home/Movies/Avatar.mp4", gvfs.getPathForURI(new URI("sftp://nico@myserver.org/home/Movies/Avatar.mp4")).getPath());
	}

}
