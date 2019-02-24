
package net.filebot.ui.sfv;


import static net.filebot.hash.VerificationUtilities.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.event.SwingPropertyChangeSupport;

import net.filebot.hash.HashType;


class ChecksumRow {

	private String name;

	private Map<File, ChecksumCell> hashes = new HashMap<File, ChecksumCell>(4);
	private State state = State.UNKNOWN;

	/**
	 * Checksum that is embedded in the file name (e.g. Test[49A93C5F].txt)
	 */
	private String embeddedChecksum;


	public static enum State {
		UNKNOWN,
		OK,
		WARNING,
		ERROR
	}


	public ChecksumRow(String name) {
		this.name = name;
		this.embeddedChecksum = getEmbeddedChecksum(name);
	}


	public String getName() {
		return name;
	}


	public State getState() {
		return state;
	}


	protected void setState(State newValue) {
		State oldValue = this.state;
		this.state = newValue;

		pcs.firePropertyChange("state", oldValue, newValue);
	}


	public ChecksumCell getChecksum(File root) {
		return hashes.get(root);
	}


	public Collection<ChecksumCell> values() {
		return Collections.unmodifiableCollection(hashes.values());
	}


	public ChecksumCell put(ChecksumCell cell) {
		ChecksumCell old = hashes.put(cell.getRoot(), cell);

		// update state immediately, don't fire property change
		state = getState(hashes.values());

		// keep state up-to-date
		cell.addPropertyChangeListener(updateStateListener);

		return old;
	}


	public void dispose() {
		// clear property change support
		for (PropertyChangeListener listener : pcs.getPropertyChangeListeners()) {
			pcs.removePropertyChangeListener(listener);
		}

		for (ChecksumCell cell : hashes.values()) {
			cell.dispose();
		}

		name = null;
		embeddedChecksum = null;
		hashes = null;
		state = null;
		pcs = null;
	}


	protected State getState(Collection<ChecksumCell> cells) {
		// check states before we bother comparing the hash values
		for (ChecksumCell cell : cells) {
			if (cell.getState() == ChecksumCell.State.ERROR) {
				// one error cell -> error state
				return State.ERROR;
			} else if (cell.getState() != ChecksumCell.State.READY) {
				// one cell that is not ready yet -> unknown state
				return State.UNKNOWN;
			}
		}

		// compare hash values
		Set<String> checksumSet = new HashSet<String>(2);
		Set<State> verdictSet = EnumSet.noneOf(State.class);

		for (HashType type : HashType.values()) {
			checksumSet.clear();

			for (ChecksumCell cell : cells) {
				String checksum = cell.getChecksum(type);

				if (checksum != null) {
					checksumSet.add(checksum.toLowerCase());
				}
			}

			verdictSet.add(getVerdict(checksumSet));
		}

		// ERROR > WARNING > OK > UNKOWN
		return Collections.max(verdictSet);
	}


	protected State getVerdict(Set<String> checksumSet) {
		if (checksumSet.size() < 1) {
			// no hash values
			return State.UNKNOWN;
		} else if (checksumSet.size() > 1) {
			// hashes don't match, something is wrong
			return State.ERROR;
		} else {
			// all hashes match
			if (embeddedChecksum != null) {
				String checksum = checksumSet.iterator().next();

				if (checksum.length() == embeddedChecksum.length() && !checksum.equalsIgnoreCase(embeddedChecksum)) {
					return State.WARNING;
				}
			}

			return State.OK;
		}
	}


	@Override
	public String toString() {
		return String.format("%s %s %s", state, name, hashes);
	}


	private final PropertyChangeListener updateStateListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if ("state".equals(evt.getPropertyName())) {
				setState(getState(hashes.values()));
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
