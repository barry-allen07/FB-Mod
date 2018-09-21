
package net.filebot.ui.sfv;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import javax.swing.SwingWorker.StateValue;
import javax.swing.event.SwingPropertyChangeSupport;

import net.filebot.hash.HashType;
import net.filebot.util.ExceptionUtilities;


class ChecksumCell {

	private final String name;
	private final File root;

	private Map<HashType, String> hashes;
	private ChecksumComputationTask task;
	private Throwable error;


	public static enum State {
		PENDING,
		PROGRESS,
		READY,
		ERROR
	}


	public ChecksumCell(String name, File root, Map<HashType, String> hashes) {
		this.name = name;
		this.root = root;
		this.hashes = hashes;
	}


	public ChecksumCell(String name, File root, ChecksumComputationTask task) {
		this.name = name;
		this.root = root;
		this.hashes = new EnumMap<HashType, String>(HashType.class);
		this.task = task;

		// forward property change events
		task.addPropertyChangeListener(taskListener);
	}


	public String getName() {
		return name;
	}


	public File getRoot() {
		return root;
	}


	public String getChecksum(HashType hash) {
		return hashes.get(hash);
	}


	public void putTask(ChecksumComputationTask computationTask) {
		if (task != null) {
			task.removePropertyChangeListener(taskListener);
			task.cancel(true);
		}

		task = computationTask;
		error = null;

		// forward property change events
		task.addPropertyChangeListener(taskListener);

		// state changed to PENDING
		pcs.firePropertyChange("state", null, getState());
	}


	public ChecksumComputationTask getTask() {
		return task;
	}


	public Throwable getError() {
		return error;
	}


	public State getState() {
		if (task != null) {
			switch (task.getState()) {
			case PENDING:
				return State.PENDING;
			default:
				return State.PROGRESS;
			}
		}

		if (error != null) {
			return State.ERROR;
		}

		return State.READY;
	}


	public void dispose() {
		// clear property change support
		for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
			pcs.removePropertyChangeListener(listener);
		}

		if (task != null) {
			task.removePropertyChangeListener(taskListener);
			task.cancel(true);
		}

		hashes = null;
		error = null;
		task = null;
		pcs = null;
	}


	@Override
	public String toString() {
		return String.format("%s %s", name, hashes);
	}

	private final PropertyChangeListener taskListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if ("state".equals(evt.getPropertyName())) {
				if (evt.getNewValue() == StateValue.DONE)
					done(evt);

				// cell state changed because worker state changed
				pcs.firePropertyChange("state", null, getState());
			} else {
				// progress events
				pcs.firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
			}
		}


		protected void done(PropertyChangeEvent evt) {
			try {
				hashes.putAll(task.get());
			} catch (Exception e) {
				Throwable cause = ExceptionUtilities.getRootCause(e);

				// ignore cancellation
				if (cause instanceof CancellationException) {
					return;
				}

				error = cause;
			} finally {
				task = null;
			}
		}
	};

	private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);


	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}


	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

}
