package net.filebot.ui.filter;

import static javax.swing.BorderFactory.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import net.filebot.util.ui.LoadingOverlayPane;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.SeriesInfo;
import net.miginfocom.swing.MigLayout;

class AttributeTool extends Tool<TableModel> {

	private JTable table = new JTable(new FileAttributesTableModel());

	public AttributeTool() {
		super("Attributes");

		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);

		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		table.setBackground(Color.white);
		table.setGridColor(new Color(0xEEEEEE));
		table.setRowHeight(25);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(createEmptyBorder());

		setLayout(new MigLayout("insets 0, fill"));
		add(new LoadingOverlayPane(scrollPane, this), "grow");
	}

	@Override
	protected TableModel createModelInBackground(List<File> root) {
		FileAttributesTableModel model = new FileAttributesTableModel();

		if (root.isEmpty()) {
			return model;
		}

		List<File> files = listFiles(root, filter(VIDEO_FILES, SUBTITLE_FILES), HUMAN_NAME_ORDER);

		for (File file : files) {
			Object metaObject = xattr.getMetaInfo(file);
			String originalName = xattr.getOriginalName(file);

			if (metaObject instanceof Episode) {
				SeriesInfo seriesInfo = ((Episode) metaObject).getSeriesInfo();
				if (seriesInfo != null) {
					model.addRow(String.format("%s::%d", seriesInfo.getDatabase(), seriesInfo.getId()), metaObject, originalName, file);
				}
			} else if (metaObject instanceof Movie) {
				Movie movie = (Movie) metaObject;
				if (movie.getTmdbId() > 0) {
					model.addRow(String.format("%s::%d", "TheMovieDB", movie.getTmdbId()), metaObject, originalName, file);
				} else if (movie.getImdbId() > 0) {
					model.addRow(String.format("%s::%d", "OMDb", movie.getImdbId()), metaObject, originalName, file);
				}
			}

			if (Thread.interrupted()) {
				throw new CancellationException();
			}
		}

		return model;
	}

	@Override
	protected void setModel(TableModel model) {
		table.setModel(model);
	}

	private static class FileAttributesTableModel extends AbstractTableModel {

		private final List<Object[]> rows = new ArrayList<Object[]>();

		public boolean addRow(Object... row) {
			if (row.length != getColumnCount())
				return false;

			return rows.add(row);
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Meta ID";
			case 1:
				return "Meta Attributes";
			case 2:
				return "Original Name";
			case 3:
				return "File Path";
			}
			return null;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return rows.get(rowIndex)[columnIndex];
		}

	}

}
