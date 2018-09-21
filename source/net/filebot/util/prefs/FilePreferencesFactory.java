package net.filebot.util.prefs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

public class FilePreferencesFactory implements PreferencesFactory {

	private final static FilePreferences userRoot = createRootNode(getBackingStoreFile());

	@Override
	public Preferences systemRoot() {
		return userRoot;
	}

	@Override
	public Preferences userRoot() {
		return userRoot;
	}

	public static FilePreferences createRootNode(Path backingStoreFile) {
		FilePreferences node = new FilePreferences(new PropertyFileBackingStore(backingStoreFile));

		// restore preferences
		try {
			node.sync();
		} catch (Exception e) {
			Logger.getLogger(FilePreferences.class.getName()).log(Level.WARNING, "Failed to load preferences: " + backingStoreFile, e);
		}

		// store preferences on exit
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				userRoot.flush();
			} catch (BackingStoreException e) {
				Logger.getLogger(FilePreferences.class.getName()).log(Level.WARNING, "Failed to save preferences: " + backingStoreFile, e);
			}
		}));

		return node;
	}

	public static Path getBackingStoreFile() {
		return Paths.get(System.getProperty("net.filebot.util.prefs.file", "prefs.properties"));
	}

}
