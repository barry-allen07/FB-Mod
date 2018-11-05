package net.filebot.ui.subtitle.upload;

import static net.filebot.Logging.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.Cursor;
import java.io.File;
import java.util.EventObject;
import java.util.List;

import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;

import net.filebot.ui.SelectDialog;
import net.filebot.util.FileUtilities;
import net.filebot.web.Movie;
import net.filebot.web.OpenSubtitlesClient;
import net.filebot.web.SubtitleSearchResult;

class MovieEditor implements TableCellEditor {

	private OpenSubtitlesClient database;

	public MovieEditor(OpenSubtitlesClient database) {
		this.database = database;
	}

	private String guessQuery(SubtitleMapping mapping) {
		String fn = FileUtilities.getName(mapping.getVideo() != null ? mapping.getVideo() : mapping.getSubtitle());

		// check if query contain an episode identifier
		String sn = getSeriesNameMatcher(true).matchByEpisodeIdentifier(fn);
		if (sn != null) {
			return stripReleaseInfo(sn, true);
		}

		return stripReleaseInfo(fn, false);
	}

	private String getFileHint(SubtitleMapping mapping) {
		File f = mapping.getVideo() != null ? mapping.getVideo() : mapping.getSubtitle();
		try {
			return getStructurePathTail(f).getPath();
		} catch (Exception e) {
			return f.getPath();
		}
	}

	private List<SubtitleSearchResult> runSearch(SubtitleMapping mapping, JTable table) throws Exception {
		String input = showInputDialog("Enter movie / series name:", guessQuery(mapping), getFileHint(mapping), table);
		if (input != null && input.length() > 0) {
			return database.searchIMDB(input);
		} else {
			return null;
		}
	}

	private void runSelect(List<SubtitleSearchResult> options, SubtitleMapping mapping, JTable table) {
		if (options == null) {
			return;
		}
		if (options.isEmpty()) {
			log.warning(String.format("%s: No results", database.getName()));
			return;
		}

		SelectDialog<Movie> dialog = new SelectDialog<Movie>(table, options);
		dialog.pack();
		dialog.setLocation(getOffsetLocation(dialog.getOwner()));
		dialog.setVisible(true);
		Movie selectedValue = dialog.getSelectedValue();
		if (selectedValue != null) {
			mapping.setIdentity(selectedValue);
			if (mapping.getIdentity() != null && mapping.getLanguage() != null && mapping.getVideo() != null) {
				mapping.setState(Status.CheckPending);
			}
		}
	}

	private void reset(Exception error, JTable table) {
		// reset window state
		getWindow(table).setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

		// print error message
		if (error != null) {
			debug.warning(error.getMessage());
		}
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		getWindow(table).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SubtitleMappingTableModel model = (SubtitleMappingTableModel) table.getModel();
		SubtitleMapping mapping = model.getData()[table.convertRowIndexToModel(row)];

		newSwingWorker(() -> {
			return runSearch(mapping, table);
		}, options -> {
			runSelect(options, mapping, table);
			reset(null, table);
		}, error -> {
			reset(error, table);
		}).execute();

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
