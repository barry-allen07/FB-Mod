package net.filebot;

import java.time.Duration;

import net.sf.ehcache.config.CacheConfiguration;

public enum CacheType {

	Persistent(Duration.ofDays(180)),

	Monthly(Duration.ofDays(60)),

	Weekly(Duration.ofDays(12)),

	Daily(Duration.ofHours(18));

	private final long timeToLiveSeconds;

	private CacheType(Duration timeToLive) {
		this.timeToLiveSeconds = timeToLive.getSeconds();
	}

	@SuppressWarnings("deprecation")
	public CacheConfiguration getConfiguration(String name) {
		// Strategy.LOCALTEMPSWAP is not restartable so we can't but use the deprecated disk persistent code (see http://stackoverflow.com/a/24623527/1514467)
		return new CacheConfiguration().name(name).maxEntriesLocalHeap(200).maxEntriesLocalDisk(0).eternal(false).timeToLiveSeconds(timeToLiveSeconds).timeToIdleSeconds(timeToLiveSeconds).overflowToDisk(true).diskPersistent(true);
	}

}
