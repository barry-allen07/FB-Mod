
package net.filebot.util.ui;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.event.SwingPropertyChangeSupport;


public abstract class AbstractBean {

	private final PropertyChangeSupport pcs;


	public AbstractBean() {
		// always notify on EDT
		pcs = new SwingPropertyChangeSupport(this, true);
	}


	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
		pcs.firePropertyChange(propertyName, oldValue, newValue);
	}


	protected void firePropertyChange(PropertyChangeEvent e) {
		pcs.firePropertyChange(e);
	}


	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}


	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}


	public PropertyChangeListener[] getPropertyChangeListeners() {
		return pcs.getPropertyChangeListeners();
	}

}
