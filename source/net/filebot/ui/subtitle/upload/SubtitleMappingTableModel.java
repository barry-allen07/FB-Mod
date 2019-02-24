package net.filebot.ui.subtitle.upload;

import static javax.swing.SwingUtilities.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

import javax.swing.table.AbstractTableModel;

import net.filebot.Language;
import net.filebot.web.Movie;

class SubtitleMappingTableModel extends AbstractTableModel {

	private SubtitleMapping[] data;
	private Runnable onCheckPending;

	public SubtitleMappingTableModel() {
		this.data = new SubtitleMapping[0];
	}

	public SubtitleMappingTableModel(Collection<SubtitleMapping> rows) {
		this.data = rows.toArray(new SubtitleMapping[rows.size()]);

		for (int i = 0; i < data.length; i++) {
			data[i].addPropertyChangeListener(new UpdateRowListener(i));
		}
	}

	public SubtitleMappingTableModel onCheckPending(Runnable onCheckPending) {
		this.onCheckPending = onCheckPending;
		return this;
	}

	public SubtitleMapping[] getData() {
		return data.clone();
	}

	@Override
	public int getColumnCount() {
		return 5;
	}

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case 0:
			return "Movie / Series";
		case 1:
			return "Video File";
		case 2:
			return "Subtitle File";
		case 3:
			return "Language";
		case 4:
			return "Status";
		}
		return null;
	}

	@Override
	public int getRowCount() {
		return data.length;
	}

	@Override
	public Object getValueAt(int row, int column) {
		switch (column) {
		case 0:
			return data[row].getIdentity();
		case 1:
			return data[row].getVideo();
		case 2:
			return data[row].getSubtitle();
		case 3:
			return data[row].getLanguage();
		case 4:
			return data[row].getStatus();
		}
		return null;
	}

	@Override
	public void setValueAt(Object value, int row, int column) {
		if (getColumnClass(column) == Language.class && value instanceof Language) {
			data[row].setLanguage((Language) value);

			if (data[row].getStatus() == Status.IdentificationRequired) {
				data[row].setState(Status.CheckPending);
			}
		}
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return (column == 0 || column == 1 || column == 3) && EnumSet.of(Status.IdentificationRequired, Status.UploadReady, Status.IllegalInput).contains(data[row].getStatus());
	}

	@Override
	public Class<?> getColumnClass(int column) {
		switch (column) {
		case 0:
			return Movie.class;
		case 1:
			return File.class;
		case 2:
			return File.class;
		case 3:
			return Language.class;
		case 4:
			return Status.class;
		}

		return null;
	}

	private class UpdateRowListener implements PropertyChangeListener {

		private final int index;

		public UpdateRowListener(int index) {
			this.index = index;
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			// update state and subtitle options
			fireTableRowsUpdated(index, index);

			if (evt.getNewValue().equals(Status.CheckPending)) {
				invokeLater(onCheckPending);
			}
		}
	}

}
