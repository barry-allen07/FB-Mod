package net.filebot.util.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.SwingWorker.StateValue;

public abstract class SwingWorkerPropertyChangeAdapter implements PropertyChangeListener {

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("progress")) {
			progress(evt);
		} else if (evt.getPropertyName().equals("state")) {
			state(evt);
		} else {
			event(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
		}
	}

	protected void state(PropertyChangeEvent evt) {
		switch ((StateValue) evt.getNewValue()) {
		case STARTED:
			started(evt);
			break;
		case DONE:
			done(evt);
			break;
		default:
			break;
		}
	}

	protected void progress(PropertyChangeEvent evt) {
	}

	protected void started(PropertyChangeEvent evt) {
	}

	protected void done(PropertyChangeEvent evt) {
	}

	protected void event(String name, Object oldValue, Object newValue) {
	}

}
