package net.filebot.ui.filter;

import static java.util.Collections.*;
import static javax.swing.BorderFactory.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import net.filebot.mediainfo.MediaInfo;
import net.filebot.mediainfo.MediaInfo.StreamKind;
import net.filebot.util.ui.LoadingOverlayPane;
import net.miginfocom.swing.MigLayout;

class MediaInfoTool extends Tool<TableModel> {

	private JTable table = new JTable(new MediaInfoTableModel());

	public MediaInfoTool() {
		super("MediaInfo");

		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);

		table.setCellSelectionEnabled(true);
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
		if (root.isEmpty()) {
			return new MediaInfoTableModel();
		}

		List<File> files = listFiles(root, filter(VIDEO_FILES, AUDIO_FILES), HUMAN_NAME_ORDER);
		Map<MediaInfoKey, String[]> data = new TreeMap<MediaInfoKey, String[]>();

		try (MediaInfo mi = new MediaInfo()) {
			IntStream.range(0, files.size()).forEach(f -> {
				try {
					mi.open(files.get(f));
					mi.snapshot().forEach((kind, streams) -> {
						IntStream.range(0, streams.size()).forEach(i -> {
							streams.get(i).forEach((name, value) -> {
								String[] values = data.computeIfAbsent(new MediaInfoKey(kind, i, name), k -> new String[files.size()]);
								values[f] = value;
							});
						});
					});
				} catch (IllegalArgumentException e) {
					debug.finest(e::toString);
				} catch (Exception e) {
					debug.warning(e::toString);
				}

				if (Thread.interrupted()) {
					throw new CancellationException();
				}
			});
		}

		return new MediaInfoTableModel(data.isEmpty() ? emptyList() : files, data);
	}

	@Override
	protected void setModel(TableModel model) {
		table.setModel(model);
		table.setAutoResizeMode(table.getRowCount() > 0 ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

		TableColumnModel columnModel = table.getColumnModel();
		IntStream.range(0, columnModel.getColumnCount()).forEach(i -> columnModel.getColumn(i).setMinWidth(150));
	}

	private static class MediaInfoKey implements Comparable<MediaInfoKey> {

		public final StreamKind kind;
		public final int stream;
		public final String name;

		private static final Pattern strip = Pattern.compile("[^a-z]", Pattern.CASE_INSENSITIVE);

		public MediaInfoKey(StreamKind kind, int stream, String name) {
			this.kind = kind;
			this.stream = stream;
			this.name = strip.matcher(name).replaceAll("");
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MediaInfoKey) {
				MediaInfoKey other = (MediaInfoKey) obj;
				return kind == other.kind && stream == other.stream && name.equals(other.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return kind.ordinal() + (stream << 8) + name.hashCode();
		}

		@Override
		public int compareTo(MediaInfoKey other) {
			if (kind != other.kind)
				return kind.compareTo(other.kind);
			if (stream != other.stream)
				return Integer.compare(stream, other.stream);
			else
				return name.compareTo(other.name);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if (kind != StreamKind.General) {
				sb.append(kind.name());
				if (stream > 0) {
					sb.append('[').append(stream).append(']');
				}
				sb.append('.');
			}
			return sb.append(name).toString();
		}

	}

	private static class MediaInfoTableModel extends AbstractTableModel {

		private final MediaInfoKey[] keys;
		private final String[][] values;
		private final String[] files;
		private final Class<?>[] columnClass;

		public MediaInfoTableModel() {
			this(emptyList(), emptyMap());
		}

		public MediaInfoTableModel(List<File> files, Map<MediaInfoKey, String[]> values) {
			this.keys = values.keySet().toArray(new MediaInfoKey[0]);
			this.values = values.values().toArray(new String[0][]);
			this.files = files.stream().map(File::getName).toArray(String[]::new);
			this.columnClass = new Class<?>[getColumnCount()];
		}

		public int getHeaderColumnCount() {
			return 1;
		}

		@Override
		public int getColumnCount() {
			return keys.length + getHeaderColumnCount();
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "File";
			default:
				return keys[column - getHeaderColumnCount()].toString();
			}
		}

		private boolean isNumber(String s) {
			try {
				Double.parseDouble(s);
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public Class<?> getColumnClass(int column) {
			int c = column - getHeaderColumnCount();
			if (c < 0) {
				return String.class;
			}

			if (columnClass[c] != null) {
				return columnClass[c];
			}

			if (IntStream.range(0, files.length).mapToObj(i -> values[c][i]).filter(Objects::nonNull).allMatch(this::isNumber)) {
				columnClass[c] = Number.class;
				return columnClass[c];
			}

			columnClass[c] = String.class;
			return columnClass[c];
		}

		@Override
		public int getRowCount() {
			return files.length;
		}

		@Override
		public String getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return files[row];
			default:
				return values[column - getHeaderColumnCount()][row];
			}
		}

	}

}
