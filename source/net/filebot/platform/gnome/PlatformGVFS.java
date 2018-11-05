
package net.filebot.platform.gnome;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.io.File;
import java.net.URI;
import java.util.List;

public class PlatformGVFS implements GVFS {

	private File gvfs;

	public PlatformGVFS(File gvfs) {
		this.gvfs = gvfs;
	}

	public File getPathForURI(URI uri) {
		return Protocol.forName(uri.getScheme()).getFile(gvfs, uri);
	}

	@Override
	public String toString() {
		return String.format("%s [%s]", getClass().getSimpleName(), gvfs);
	}

	public static enum Protocol {

		FILE {

			@Override
			public File getFile(File gvfs, URI uri) {
				return new File(uri);
			}

			@Override
			public String getPath(URI uri) {
				return new File(uri).getPath();
			}
		},

		SMB {

			@Override
			public String getPath(URI uri) {
				// e.g. smb://10.0.1.5/data/Movies/Avatar.mp4 -> /run/user/1000/gvfs/smb-share:server=10.0.1.5,share=data/Movies/Avatar.mp4
				StringBuilder s = new StringBuilder("smb-share:");
				s.append("server=").append(uri.getHost());
				if (uri.getUserInfo() != null) {
					s.append(",user=").append(uri.getUserInfo());
				}
				s.append(",share=").append(uri.getPath().substring(1));
				return s.toString();
			}
		},

		AFP {

			@Override
			public String getPath(URI uri) {
				// e.g. afp://reinhard@10.0.1.5/data/Movies/Avatar.mp4 -> /run/user/1000/gvfs/afp-volume:host=10.0.1.5,user=reinhard,volume=data/Movies/Avatar.mp4
				StringBuilder s = new StringBuilder("afp-volume:");
				s.append("host=").append(uri.getHost());
				if (uri.getUserInfo() != null) {
					s.append(",user=").append(uri.getUserInfo());
				}
				s.append(",volume=").append(uri.getPath().substring(1));
				return s.toString();
			}
		},

		SFTP {

			@Override
			public String getPath(URI uri) {
				// e.g. sftp://reinhard@10.0.1.5/home/Movies/Avatar.mp4 -> /run/user/1000/gvfs/sftp:host=10.0.1.5,user=reinhard/home/Movies/Avatar.mp4
				StringBuilder s = new StringBuilder("sftp:");
				s.append("host=").append(uri.getHost());
				if (uri.getUserInfo() != null) {
					s.append(",user=").append(uri.getUserInfo());
				}
				s.append(uri.getPath());
				return s.toString();
			}
		};

		public abstract String getPath(URI uri);

		public File getFile(File gvfs, URI uri) {
			return new File(gvfs, getPath(uri));
		}

		public static List<String> names() {
			return stream(values()).map(Enum::name).collect(toList());
		}

		public static Protocol forName(String name) {
			for (Protocol protocol : values()) {
				if (protocol.name().equalsIgnoreCase(name)) {
					return protocol;
				}
			}

			throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
		}

	}

}
