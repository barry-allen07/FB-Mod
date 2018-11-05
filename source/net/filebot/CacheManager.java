package net.filebot;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;
import java.util.logging.Level;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;

public class CacheManager {

	private static final CacheManager instance = new CacheManager();

	public static CacheManager getInstance() {
		return instance;
	}

	private final File diskStore;
	private final net.sf.ehcache.CacheManager manager;

	public CacheManager() {
		try {
			this.diskStore = acquireDiskStore();
			this.manager = net.sf.ehcache.CacheManager.create(new Configuration().diskStore(new DiskStoreConfiguration().path(diskStore.getPath())));
		} catch (IOException e) {
			throw new CacheException(e);
		}
	}

	public synchronized Cache getCache(String name, CacheType type) {
		if (!manager.cacheExists(name)) {
			manager.addCache(new net.sf.ehcache.Cache(type.getConfiguration(name)));
		}
		return new Cache(manager.getCache(name), type);
	}

	public synchronized void clearAll() {
		manager.clearAll();

		// clear all caches that have not been added yet
		clearDiskStore(diskStore);
	}

	public synchronized void shutdown() {
		manager.shutdown();
	}

	private void clearDiskStore(File cache) {
		getChildren(cache, FILES).stream().filter(f -> !f.getName().startsWith(".")).forEach(f -> {
			try {
				delete(f);
			} catch (Exception e) {
				debug.warning(format("Failed to delete cache: %s => %s", f, e));
			}
		});
	}

	private File acquireDiskStore() throws IOException {
		for (int i = 0; i < 10; i++) {
			File cache = ApplicationFolder.Cache.resolve(String.valueOf(i));

			// make sure cache is readable and writable
			createFolders(cache);

			File lockFile = new File(cache, ".lock");
			boolean isNewCache = !lockFile.exists();

			FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
			FileLock lock = channel.tryLock();

			if (lock != null) {
				debug.config(format("Using persistent disk cache %s", cache));

				int applicationRevision = getApplicationRevisionNumber();
				int cacheRevision = 0;

				if (channel.size() > 0) {
					try {
						cacheRevision = new Scanner(channel, "UTF-8").nextInt();
					} catch (Exception e) {
						debug.log(Level.WARNING, e, e::toString);
					}
				}

				if (cacheRevision != applicationRevision && applicationRevision > 0 && !isNewCache) {
					debug.config(format("Current application version (r%d) does not match cache version (r%d): reset cache", applicationRevision, cacheRevision));

					// tag cache with new revision number
					isNewCache = true;

					// delete all files related to previous cache instances
					clearDiskStore(cache);
				}

				if (isNewCache) {
					// set new cache revision
					channel.position(0);
					channel.write(UTF_8.encode(String.valueOf(applicationRevision)));
					channel.truncate(channel.position());
				}

				// make sure to orderly shutdown cache
				Runtime.getRuntime().addShutdownHook(new ShutdownHook(this, channel, lock));

				// cache for this application instance is successfully set up and locked
				return cache;
			}

			// try next lock file
			channel.close();
		}

		// serious error, abort
		throw new IOException("Unable to acquire cache lock: " + ApplicationFolder.Cache.get().getAbsolutePath());
	}

	private static class ShutdownHook extends Thread {

		private final CacheManager manager;
		private final FileChannel channel;
		private final FileLock lock;

		public ShutdownHook(CacheManager manager, FileChannel channel, FileLock lock) {
			this.manager = manager;
			this.channel = channel;
			this.lock = lock;
		}

		@Override
		public void run() {
			try {
				manager.shutdown();
			} catch (Exception e) {
				debug.log(Level.WARNING, "Shutdown hook failed: shutdown", e);
			}

			try {
				lock.release();
			} catch (Exception e) {
				debug.log(Level.WARNING, "Shutdown hook failed: release", e);
			}

			try {
				channel.close();
			} catch (Exception e) {
				debug.log(Level.WARNING, "Shutdown hook failed: close", e);
			}
		}

	}

}
