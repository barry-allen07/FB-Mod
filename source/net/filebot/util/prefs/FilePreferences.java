package net.filebot.util.prefs;

import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

public class FilePreferences extends AbstractPreferences {

	protected PropertyFileBackingStore store;

	public FilePreferences(PropertyFileBackingStore store) {
		super(null, "");

		this.store = store;
	}

	protected FilePreferences(FilePreferences parent, String name) {
		super(parent, name);

		this.store = parent.store;
	}

	protected String getNodeKey() {
		return absolutePath().substring(1);
	}

	@Override
	protected void putSpi(String key, String value) {
		store.setValue(getNodeKey(), key, value);
	}

	@Override
	protected String getSpi(String key) {
		return store.getValue(getNodeKey(), key);
	}

	@Override
	protected void removeSpi(String key) {
		store.removeValue(getNodeKey(), key);
	}

	@Override
	protected void removeNodeSpi() throws BackingStoreException {
		store.removeNode(getNodeKey());
	}

	@Override
	protected String[] keysSpi() throws BackingStoreException {
		return store.getKeys(getNodeKey());
	}

	@Override
	protected String[] childrenNamesSpi() throws BackingStoreException {
		return store.getChildren(getNodeKey());
	}

	@Override
	protected FilePreferences childSpi(String name) {
		return new FilePreferences(this, name);
	}

	@Override
	public void sync() throws BackingStoreException {
		// if the backing store naturally syncs an entire subtree at once, the implementer is encouraged to override sync(), rather than merely overriding syncSpi()
		syncSpi();
	}

	@Override
	protected void syncSpi() throws BackingStoreException {
		try {
			store.sync();
		} catch (Exception e) {
			throw new BackingStoreException(e);
		}
	}

	@Override
	public void flush() throws BackingStoreException {
		// if the backing store naturally flushes an entire subtree at once, the implementer is encouraged to override flush(), rather than merely overriding flushSpi()
		flushSpi();
	}

	@Override
	protected void flushSpi() throws BackingStoreException {
		try {
			store.flush();
		} catch (Exception e) {
			throw new BackingStoreException(e);
		}
	}

}
