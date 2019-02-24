
package net.filebot.web;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.filebot.util.DefaultThreadFactory;

public class FloodLimit {

	private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, new DefaultThreadFactory("FloodLimitTimer", Thread.NORM_PRIORITY, true));

	private final Semaphore permits;

	private final long releaseDelay;
	private final TimeUnit timeUnit;

	public FloodLimit(int permitLimit, long releaseDelay, TimeUnit timeUnit) {
		this.permits = new Semaphore(permitLimit, true);
		this.releaseDelay = releaseDelay;
		this.timeUnit = timeUnit;
	}

	public ScheduledFuture<?> acquirePermit() throws InterruptedException {
		permits.acquire();
		return TIMER.schedule(this::releasePermit, releaseDelay, timeUnit);
	}

	protected void releasePermit() {
		permits.release();
	}

}
