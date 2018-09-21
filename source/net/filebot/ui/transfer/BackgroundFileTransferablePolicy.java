package net.filebot.ui.transfer;

import static net.filebot.ui.transfer.FileTransferable.*;

import java.awt.datatransfer.Transferable;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.SwingPropertyChangeSupport;

public abstract class BackgroundFileTransferablePolicy<V> extends FileTransferablePolicy {

	public static final String LOADING_PROPERTY = "loading";

	private final ThreadLocal<BackgroundWorker> threadLocalWorker = new ThreadLocal<BackgroundWorker>();

	private final List<BackgroundWorker> workers = new ArrayList<BackgroundWorker>(2);

	@Override
	public void handleTransferable(Transferable tr, TransferAction action) throws Exception {
		List<File> files = getFilesFromTransferable(tr);

		if (action != TransferAction.ADD) {
			clear();
		}

		handleInBackground(files, action);
	}

	protected void handleInBackground(List<File> files, TransferAction action) {
		new BackgroundWorker(files, action).execute();
	}

	@Override
	protected void clear() {
		// stop other workers on clear (before starting new worker)
		reset();
	}

	public void reset() {
		synchronized (workers) {
			if (workers.size() > 0) {
				// avoid ConcurrentModificationException by iterating over a copy
				for (BackgroundWorker worker : new ArrayList<BackgroundWorker>(workers)) {
					// worker.cancel() will invoke worker.done() which will invoke workers.remove(worker)
					worker.cancel(true);
				}
			}
		}
	}

	public boolean isLoading() {
		synchronized (workers) {
			return !workers.isEmpty();
		}
	}

	protected abstract void process(List<V> chunks);

	protected abstract void process(Exception exception);

	protected final void publish(V[] chunks) {
		BackgroundWorker worker = threadLocalWorker.get();

		if (worker == null) {
			// fail if a non-background-worker thread is trying to access the thread-local worker object
			throw new IllegalThreadStateException("Illegal access thread");
		}

		worker.offer(chunks);
	}

	protected final void publish(Exception exception) {
		SwingUtilities.invokeLater(() -> process(exception));
	}

	protected class BackgroundWorker extends SwingWorker<Object, V> {

		private final List<File> files;
		private final TransferAction action;

		public BackgroundWorker(List<File> files, TransferAction action) {
			this.files = files;
			this.action = action;

			// register this worker
			synchronized (workers) {
				if (workers.add(this) && workers.size() == 1) {
					swingPropertyChangeSupport.firePropertyChange(LOADING_PROPERTY, false, true);
				}
			}
		}

		@Override
		protected Object doInBackground() throws Exception {
			// associate this worker with the current (background) thread
			threadLocalWorker.set(this);

			try {
				load(files, action);
			} finally {
				threadLocalWorker.remove();
			}

			return null;
		}

		public void offer(V[] chunks) {
			if (!isCancelled()) {
				publish(chunks);
			}
		}

		@Override
		protected void process(List<V> chunks) {
			if (!isCancelled()) {
				BackgroundFileTransferablePolicy.this.process(chunks);
			}
		}

		@Override
		protected void done() {
			if (!isCancelled()) {
				try {
					// check for exception
					get();
				} catch (Exception e) {
					BackgroundFileTransferablePolicy.this.process(e);
				}
			}

			// unregister worker
			synchronized (workers) {
				if (workers.remove(this) && workers.isEmpty()) {
					swingPropertyChangeSupport.firePropertyChange(LOADING_PROPERTY, true, false);
				}
			}
		}
	}

	protected final PropertyChangeSupport swingPropertyChangeSupport = new SwingPropertyChangeSupport(this, true);

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		swingPropertyChangeSupport.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		swingPropertyChangeSupport.removePropertyChangeListener(listener);
	}
}
