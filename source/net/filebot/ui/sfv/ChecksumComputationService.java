package net.filebot.ui.sfv;

import static net.filebot.Logging.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import net.filebot.Settings;
import net.filebot.util.DefaultThreadFactory;

class ChecksumComputationService {

	public static final String TASK_COUNT_PROPERTY = "taskCount";

	private final Set<ThreadPoolExecutor> executors = new HashSet<ThreadPoolExecutor>(4);

	private final AtomicInteger completedTaskCount = new AtomicInteger(0);
	private final AtomicInteger totalTaskCount = new AtomicInteger(0);

	private final int threadPoolSize = Settings.getPreferredThreadPoolSize();

	public ExecutorService newExecutor() {
		return new ChecksumComputationExecutor();
	}

	public void reset() {
		synchronized (executors) {
			for (ExecutorService executor : executors) {
				for (Runnable runnable : executor.shutdownNow()) {
					// cancel all remaining tasks
					if (runnable instanceof Future) {
						((Future<?>) runnable).cancel(false);
					}
				}
			}

			totalTaskCount.set(0);
			completedTaskCount.set(0);

			executors.clear();
		}

		pcs.firePropertyChange(TASK_COUNT_PROPERTY, -1, getTaskCount());
	}

	/**
	 * Get the number of active executors that are associated with this {@link ChecksumComputationService}.
	 *
	 * @return number of active executors
	 * @see {@link #newExecutor()}
	 */
	public int getActiveCount() {
		synchronized (executors) {
			return executors.size();
		}
	}

	public int getTaskCount() {
		return totalTaskCount.get() - completedTaskCount.get();
	}

	public int getTotalTaskCount() {
		return totalTaskCount.get();
	}

	public int getCompletedTaskCount() {
		return completedTaskCount.get();
	}

	public void purge() {
		synchronized (executors) {
			for (ThreadPoolExecutor executor : executors) {
				executor.purge();
			}
		}
	}

	private class ChecksumComputationExecutor extends ThreadPoolExecutor {

		public ChecksumComputationExecutor() {
			super(1, threadPoolSize, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DefaultThreadFactory("ChecksumComputationPool", Thread.MIN_PRIORITY));

			synchronized (executors) {
				if (executors.add(this) && executors.size() == 1) {
					// first executor of a new session, reset counts
					totalTaskCount.set(0);
					completedTaskCount.set(0);
				}
			}

			prestartAllCoreThreads();
		}

		protected int getPreferredPoolSize() {
			// for a few files, use one thread
			// for lots of files, use multiple threads
			// e.g 50 files ~ 1 thread, 200 files ~ 2 threads, 1000 files ~ 3 threads, 40000 files ~ 5 threads
			return (int) Math.max(1, Math.round(Math.sqrt(threadPoolSize) + Math.log10(getQueue().size()) - 1));
		}

		@Override
		public void execute(Runnable command) {
			int preferredPoolSize = getPreferredPoolSize();

			if (preferredPoolSize > 0 && preferredPoolSize <= getMaximumPoolSize() && getCorePoolSize() < preferredPoolSize) {
				try {
					setCorePoolSize(preferredPoolSize);
				} catch (Exception e) {
					log.log(Level.WARNING, "Failed to set core pool size: " + preferredPoolSize, e);
				}
			}

			synchronized (this) {
				super.execute(command);
			}

			totalTaskCount.incrementAndGet();

			pcs.firePropertyChange(TASK_COUNT_PROPERTY, getTaskCount() - 1, getTaskCount());
		}

		@Override
		public void purge() {
			int delta = 0;

			synchronized (this) {
				delta += getQueue().size();
				super.purge();
				delta -= getQueue().size();
			}

			if (delta > 0) {
				// subtract removed tasks from task count
				totalTaskCount.addAndGet(-delta);

				pcs.firePropertyChange(TASK_COUNT_PROPERTY, getTaskCount() + delta, getTaskCount());
			}
		}

		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);

			if (isValid()) {
				if (r instanceof Future && ((Future<?>) r).isCancelled()) {
					totalTaskCount.decrementAndGet();
				} else {
					completedTaskCount.incrementAndGet();
				}

				pcs.firePropertyChange(TASK_COUNT_PROPERTY, getTaskCount() + 1, getTaskCount());
			}
		}

		protected boolean isValid() {
			synchronized (executors) {
				return executors.contains(this);
			}
		}

		@Override
		protected void terminated() {
			synchronized (executors) {
				executors.remove(this);
			}
		}
	}

	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

}
