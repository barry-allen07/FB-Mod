package net.filebot;

import static net.filebot.License.*;
import static net.filebot.Logging.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import net.filebot.UserFiles.FileChooser;
import net.filebot.cli.ArgumentBean;
import net.filebot.util.PreferencesList;
import net.filebot.util.PreferencesMap;
import net.filebot.util.PreferencesMap.JsonAdapter;
import net.filebot.util.PreferencesMap.PreferencesEntry;
import net.filebot.util.PreferencesMap.StringAdapter;

public final class Settings {

	public static final LicenseModel LICENSE = getLicenseModel();

	public static String getApplicationName() {
		return getApplicationProperty("application.name");
	}

	public static String getApplicationVersion() {
		return getApplicationProperty("application.version");
	}

	public static int getApplicationRevisionNumber() {
		try {
			return Integer.parseInt(getApplicationProperty("application.revision"));
		} catch (Exception e) {
			return 0;
		}
	}

	public static String getApplicationProperty(String key) {
		return ResourceBundle.getBundle(Settings.class.getName(), Locale.ROOT).getString(key);
	}

	public static String getApiKey(String name) {
		return getApplicationProperty("apikey." + name);
	}

	public static boolean isUnixFS() {
		return Boolean.parseBoolean(System.getProperty("unixfs"));
	}

	public static boolean useNativeShell() {
		return Boolean.parseBoolean(System.getProperty("useNativeShell"));
	}

	public static boolean useGVFS() {
		return Boolean.parseBoolean(System.getProperty("useGVFS"));
	}

	public static boolean useExtendedFileAttributes() {
		return Boolean.parseBoolean(System.getProperty("useExtendedFileAttributes"));
	}

	public static boolean useCreationDate() {
		return Boolean.parseBoolean(System.getProperty("useCreationDate"));
	}

	public static boolean useRenameHistory() {
		return Boolean.parseBoolean(System.getProperty("application.rename.history", "true"));
	}

	public static String getApplicationDeployment() {
		return System.getProperty("application.deployment", "jar");
	}

	public static boolean isAppStore() {
		return isApplicationDeployment("appx", "mas");
	}

	public static boolean isWindowsApp() {
		return isApplicationDeployment("appx", "msi", "zip");
	}

	public static boolean isUbuntuApp() {
		return isApplicationDeployment("snap");
	}

	public static boolean isLinuxApp() {
		return isApplicationDeployment("snap", "deb", "tar", "spk", "qpkg", "docker", "aur");
	}

	public static boolean isMacApp() {
		return isApplicationDeployment("mas", "app", "pkg");
	}

	public static boolean isMacSandbox() {
		return isApplicationDeployment("mas");
	}

	public static boolean isUWP() {
		return isApplicationDeployment("appx");
	}

	public static boolean isAutoUpdateEnabled() {
		return isApplicationDeployment("appx", "mas", "snap");
	}

	private static boolean isApplicationDeployment(String... ids) {
		String current = getApplicationDeployment();
		for (String id : ids) {
			if (current != null && current.equals(id))
				return true;
		}
		return false;
	}

	public static FileChooser getPreferredFileChooser() {
		return FileChooser.valueOf(System.getProperty("net.filebot.UserFiles.fileChooser", "Swing"));
	}

	public static int getPreferredThreadPoolSize() {
		try {
			String threadPool = System.getProperty("threadPool");
			if (threadPool != null) {
				return Integer.parseInt(threadPool);
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
		}

		return Runtime.getRuntime().availableProcessors();
	}

	public static LicenseModel getLicenseModel() {
		if (isUWP())
			return LicenseModel.MicrosoftStore;
		if (isMacSandbox())
			return LicenseModel.MacAppStore;

		return LicenseModel.PGPSignedMessage;
	}

	public static void configureLicense(File file) {
		try {
			log.info(importLicenseFile(file) + " has been activated successfully.");
		} catch (Throwable e) {
			log.severe("License Error: " + e.getMessage());
		}
	}

	public static String getAppStoreName() {
		if (isMacApp())
			return "Mac App Store";
		if (isWindowsApp())
			return "Microsoft Store";
		if (isUbuntuApp())
			return "Ubuntu Software Center";

		return null;
	}

	public static String getAppStoreLink() {
		if (isMacApp())
			return getApplicationProperty("link.mas");
		if (isWindowsApp())
			return getApplicationProperty("link.mws");
		if (isUbuntuApp())
			return getApplicationProperty("link.usc");

		return null;
	}

	public static String getPurchaseURL() {
		return getApplicationProperty("link.app.purchase") + '#' + getApplicationDeployment();
	}

	public static String getEmbeddedHelpURL() {
		// add #hash so we can dynamically adjust the slides for the various platforms via JavaScript
		return getApplicationProperty("link.app.help") + '#' + getApplicationDeployment();
	}

	public static String getWindowTitle() {
		return isAutoUpdateEnabled() ? getApplicationName() : String.format("%s %s", getApplicationName(), getApplicationVersion());
	}

	public static String getApplicationIdentifier() {
		return String.format("%s %s (r%d)", getApplicationName(), getApplicationVersion(), getApplicationRevisionNumber());
	}

	public static String getJavaRuntimeIdentifier() {
		return String.format("%s %s", System.getProperty("java.runtime.name"), System.getProperty("java.version"));
	}

	public static String getSystemIdentifier() {
		return String.format("%s %s (%s)", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
	}

	private static ArgumentBean applicationArguments;

	public static void setApplicationArguments(ArgumentBean args) {
		applicationArguments = args;
	}

	public static ArgumentBean getApplicationArguments() {
		return applicationArguments;
	}

	public static Settings forPackage(Class<?> type) {
		return new Settings(Preferences.userNodeForPackage(type));
	}

	private final Preferences prefs;

	private Settings(Preferences prefs) {
		this.prefs = prefs;
	}

	public Settings node(String nodeName) {
		return new Settings(prefs.node(nodeName));
	}

	public String get(String key) {
		return get(key, null);
	}

	public String get(String key, String def) {
		return prefs.get(key, def);
	}

	public void put(String key, String value) {
		if (value != null) {
			prefs.put(key, value);
		} else {
			remove(key);
		}
	}

	public void remove(String key) {
		prefs.remove(key);
	}

	public PreferencesEntry<String> entry(String key) {
		return new PreferencesEntry<String>(prefs, key, new StringAdapter());
	}

	public PreferencesMap<String> asMap() {
		return PreferencesMap.map(prefs);
	}

	public <T> PreferencesMap<T> asMap(Class<T> cls) {
		return PreferencesMap.map(prefs, new JsonAdapter(cls));
	}

	public PreferencesList<String> asList() {
		return PreferencesList.map(prefs);
	}

	public <T> PreferencesList<T> asList(Class<T> cls) {
		return PreferencesList.map(prefs, new JsonAdapter(cls));
	}

	public void clear() {
		try {
			// remove child nodes
			for (String nodeName : prefs.childrenNames()) {
				prefs.node(nodeName).removeNode();
			}

			// remove entries
			prefs.clear();
		} catch (BackingStoreException e) {
			debug.warning(e.getMessage());
		}
	}

	public static void store(File f) {
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
			Preferences.userRoot().exportSubtree(out);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

	public static void restore(File f) {
		try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
			Preferences.importPreferences(in);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

}
