package net.filebot.ui.sfv;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import net.filebot.hash.HashType;
import net.filebot.util.FastFile;
import net.filebot.util.FileUtilities;

class ChecksumTableModel extends AbstractTableModel {

	private final IndexedMap<String, ChecksumRow> rows = new IndexedMap<String, ChecksumRow>() {

		@Override
		public String key(ChecksumRow value) {
			return value.getName();
		}
	};

	private final List<File> checksumColumns = new ArrayList<File>(4);

	public static final String HASH_TYPE_PROPERTY = "hashType";
	private HashType hashType = HashType.SFV;

	@Override
	public String getColumnName(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return "State";
		case 1:
			return "Name";
		default:
			// works for files too and simply returns the name unchanged
			return FileUtilities.getFolderName(getColumnRoot(columnIndex));
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return ChecksumRow.State.class;
		case 1:
			return String.class;
		default:
			return ChecksumCell.class;
		}
	}

	protected int getColumnIndex(ChecksumCell cell) {
		int index = checksumColumns.indexOf(cell.getRoot());

		if (index < 0)
			return -1;

		// add checksum column offset
		return index + 2;
	}

	public File getColumnRoot(int columnIndex) {
		// substract checksum column offset
		return checksumColumns.get(columnIndex - 2);
	}

	public boolean isVerificationColumn(int columnIndex) {
		return columnIndex >= 2 && getColumnRoot(columnIndex).isFile();
	}

	public List<File> getChecksumColumns() {
		return Collections.unmodifiableList(checksumColumns);
	}

	@Override
	public int getColumnCount() {
		// add checksum column offset
		return checksumColumns.size() + 2;
	}

	protected int getRowIndex(ChecksumRow row) {
		return rows.getIndexByKey(row.getName());
	}

	protected int getRowIndex(ChecksumCell cell) {
		return rows.getIndexByKey(cell.getName());
	}

	public List<ChecksumRow> rows() {
		return Collections.unmodifiableList(rows);
	}

	@Override
	public int getRowCount() {
		return rows.size();
	}

	public void setHashType(HashType hashType) {
		HashType old = this.hashType;

		this.hashType = hashType;

		// update table
		fireTableDataChanged();

		// notify listeners
		pcs.firePropertyChange(HASH_TYPE_PROPERTY, old, hashType);
	}

	public HashType getHashType() {
		return hashType;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		ChecksumRow row = rows.get(rowIndex);

		switch (columnIndex) {
		case 0:
			return row.getState();
		case 1:
			return row.getName();
		}

		ChecksumCell cell = row.getChecksum(getColumnRoot(columnIndex));

		// empty cell
		if (cell == null)
			return null;

		switch (cell.getState()) {
		case READY:
			return cell.getChecksum(hashType);
		case ERROR:
			return cell.getError();
		default: // PENDING or PROGRESS
			return cell.getTask();
		}
	}

	public void addAll(Collection<ChecksumCell> values) {
		List<ChecksumCell> replacements = new ArrayList<ChecksumCell>();

		int rowCount = getRowCount();
		int columnCount = getColumnCount();

		for (ChecksumCell cell : values) {
			int rowIndex = getRowIndex(cell);

			ChecksumRow row;

			if (rowIndex >= 0) {
				// get existing row
				row = rows.get(rowIndex);
			} else {
				// add new row
				row = new ChecksumRow(cell.getName());
				row.addPropertyChangeListener(stateListener);
				rows.add(row);
			}

			// add cell to row
			ChecksumCell old = row.put(cell);

			// dispose of old cell
			if (old != null) {
				old.dispose();
				replacements.add(cell);
			}

			// listen to changes (progress, state)
			cell.addPropertyChangeListener(progressListener);

			if (!checksumColumns.contains(cell.getRoot())) {
				checksumColumns.add(new FastFile(cell.getRoot()));
			}
		}

		// fire table events
		if (columnCount != getColumnCount()) {
			// number of columns has changed
			fireTableStructureChanged();
			return;
		}

		for (ChecksumCell replacement : replacements) {
			int row = getRowIndex(replacement);

			// update this row
			fireTableRowsUpdated(row, row);
		}

		if (rowCount != getRowCount()) {
			// some rows have been inserted
			fireTableRowsInserted(rowCount, getRowCount() - 1);
		}
	}

	public void remove(int... index) {
		// sort index array
		Arrays.sort(index);

		for (int i : index) {
			rows.get(i).dispose();
		}

		// remove rows
		rows.removeAll(index);

		fireTableRowsDeleted(index[0], index[index.length - 1]);
	}

	public void clear() {
		checksumColumns.clear();
		rows.clear();

		fireTableStructureChanged();
	}

	private final PropertyChangeListener stateListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			int row = getRowIndex((ChecksumRow) evt.getSource());

			if (row >= 0) {
				// update row
				fireTableRowsUpdated(row, row);
			}
		}
	};

	private final PropertyChangeListener progressListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			ChecksumCell cell = (ChecksumCell) evt.getSource();

			int row = getRowIndex(cell);
			int column = getColumnIndex(cell);

			if (row >= 0 && column >= 0) {
				fireTableCellUpdated(row, column);
			}
		}
	};

	protected static abstract class IndexedMap<K, V> extends AbstractList<V> {

		private final Map<K, Integer> indexMap = new HashMap<K, Integer>(64);
		private final List<V> list = new ArrayList<V>(64);

		public abstract K key(V value);

		@Override
		public V get(int index) {
			return list.get(index);
		}

		public int getIndexByKey(K key) {
			Integer index = indexMap.get(key);

			if (index == null)
				return -1;

			return index;
		}

		@Override
		public boolean add(V value) {
			K key = key(value);
			Integer index = indexMap.get(key);

			if (index == null && list.add(value)) {
				indexMap.put(key, lastIndexOf(value));
				return true;
			}

			return false;
		}

		public void removeAll(int... index) {
			// sort index array
			Arrays.sort(index);

			// remove in reverse
			for (int i = index.length - 1; i >= 0; i--) {
				V value = list.remove(index[i]);
				indexMap.remove(key(value));
			}

			updateIndexMap();
		}

		private void updateIndexMap() {
			for (int i = 0; i < list.size(); i++) {
				indexMap.put(key(list.get(i)), i);
			}
		}

		@Override
		public int size() {
			return list.size();
		}

		@Override
		public void clear() {
			list.clear();
			indexMap.clear();
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
