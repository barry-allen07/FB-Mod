package net.filebot;

import static net.filebot.Logging.*;
import static net.filebot.Settings.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

public enum ApplicationFolder {

	// real user home (the user.home will point to the application-specific container in sandbox environments)
	UserHome(System.getProperty(isMacSandbox() ? "UserHome" : "user.home")),

	AppData(System.getProperty("application.dir", UserHome.resolve(".filebot").getPath())),

	TemporaryFiles(System.getProperty("java.io.tmpdir")),

	Cache(System.getProperty("application.cache", AppData.resolve("cache").getPath()));

	private File path;

	ApplicationFolder(String path) {
		try {
			// use canonical file path
			Path f = Paths.get(path);

			// create folders if necessary
			if (!Files.exists(f, LinkOption.NOFOLLOW_LINKS)) {
				Files.createDirectories(f);
			}

			this.path = f.toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
		} catch (IOException e) {
			debug.log(Level.WARNING, e, e::toString);

			// default to file path as is
			this.path = new File(path).getAbsoluteFile();
		}
	}

	public File get() {
		return path;
	}

	public File resolve(String name) {
		return new File(path, name);
	}

}
