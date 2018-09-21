package net.filebot.ui.subtitle.upload;

import static net.filebot.MediaTypes.*;
import static net.filebot.UserFiles.*;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.EventObject;
import java.util.List;

import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;

class FileEditor implements TableCellEditor {

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		SubtitleMappingTableModel model = (SubtitleMappingTableModel) table.getModel();
		SubtitleMapping mapping = model.getData()[table.convertRowIndexToModel(row)];

		List<File> files = showLoadDialogSelectFiles(false, false, mapping.getSubtitle().getParentFile(), VIDEO_FILES, "Select Video File", new ActionEvent(table, ActionEvent.ACTION_PERFORMED, "Select"));
		if (files.size() > 0) {
			mapping.setVideo(files.get(0));
			mapping.setState(Status.CheckPending);
		}
		return null;
	}

	@Override
	public boolean stopCellEditing() {
		return true;
	}

	@Override
	public boolean shouldSelectCell(EventObject evt) {
		return false;
	}

	@Override
	public void removeCellEditorListener(CellEditorListener listener) {
	}

	@Override
	public boolean isCellEditable(EventObject evt) {
		return true;
	}

	@Override
	public Object getCellEditorValue() {
		return null;
	}

	@Override
	public void cancelCellEditing() {
	}

	@Override
	public void addCellEditorListener(CellEditorListener evt) {
	}

}
