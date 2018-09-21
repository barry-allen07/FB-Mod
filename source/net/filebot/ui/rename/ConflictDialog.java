package net.filebot.ui.rename;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.ResourceManager;
import net.filebot.UserFiles;
import net.miginfocom.swing.MigLayout;

class ConflictDialog extends JDialog {

	private ConflictTableModel model = new ConflictTableModel();
	private boolean cancel = true;

	public ConflictDialog(Window owner, List<Conflict> conflicts) {
		super(owner, "Conflicting Files", ModalityType.DOCUMENT_MODAL);

		model.setData(conflicts);

		JTable table = new JTable(model);
		table.setDefaultRenderer(File.class, new FileRenderer());
		table.setFillsViewportHeight(true);
		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

		table.setRowSelectionAllowed(true);
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		table.getColumnModel().getColumn(0).setMaxWidth(40);
		table.setRowHeight(25);
		table.setPreferredScrollableViewportSize(new Dimension(500, 250));

		table.addMouseListener(new OpenListener());

		// force white background (e.g. GTK LaF default table background is gray)
		setBackground(Color.WHITE);

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets dialog, nogrid, fill", "", "[fill][pref!]"));

		c.add(new JScrollPane(table), "grow, wrap");
		c.add(newButton("Cancel", ResourceManager.getIcon("dialog.cancel"), this::cancel), "tag left");
		c.add(newButton("Continue", ResourceManager.getIcon("dialog.continue"), this::ignore), "tag ok");

		JButton b = newButton("Override", ResourceManager.getIcon("dialog.continue.invalid"), this::override);
		b.setEnabled(conflicts.stream().anyMatch(it -> it.override));
		b.addActionListener(evt -> b.setEnabled(false));
		c.add(b, "tag next");

		// focus "Continue" button
		SwingUtilities.invokeLater(c.getComponent(2)::requestFocusInWindow);

		installAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), newAction("Cancel", this::cancel));

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(365, 280));
		pack();
	}

	public boolean cancel() {
		return cancel;
	}

	public List<Conflict> getConflicts() {
		return model.getData();
	}

	private void override(ActionEvent evt) {
		// delete existing destination files and create new data model
		List<Conflict> data = model.getData().stream().map(c -> {
			// safety check
			if (!c.override) {
				return c;
			}

			try {
				UserFiles.trash(c.destination);
			} catch (Exception e) {
				return new Conflict(c.source, c.destination, singletonList(e.getMessage()), false);
			}

			// resolved => remove conflict
			return null;
		}).filter(Objects::nonNull).collect(toList());

		// insert new conflict data
		model.setData(data);

		// continue if there are no more conflicts
		if (data.isEmpty()) {
			ignore(evt);
		}
	}

	public void ignore(ActionEvent evt) {
		cancel = false;
		setVisible(false);
	}

	public void cancel(ActionEvent evt) {
		cancel = true;
		setVisible(false);
	}

	public static class Conflict {

		public final File source;
		public final File destination;

		public final List<Object> issues;
		public final boolean override;

		public Conflict(File source, File destination, List<Object> issues, boolean override) {
			this.source = source;
			this.destination = destination;
			this.issues = issues;
			this.override = override;
		}

	}

	private static class ConflictTableModel extends AbstractTableModel {

		private Conflict[] data = new Conflict[0];

		public void setData(List<Conflict> data) {
			this.data = data.toArray(new Conflict[0]);

			// update table
			fireTableDataChanged();
		}

		private List<Conflict> getData() {
			return unmodifiableList(asList(data));
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "";
			case 1:
				return "Issue";
			case 2:
				return "Source";
			case 3:
				return "Destination";
			case 4:
			}
			return null;
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public int getRowCount() {
			return data.length;
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return Icon.class;
			case 1:
				return String.class;
			default:
				return File.class;
			}
		}

		@Override
		public Object getValueAt(int row, int column) {
			Conflict conflict = data[row];

			switch (column) {
			case 0:
				return ResourceManager.getIcon(conflict.issues.isEmpty() ? "status.ok" : "status.error");
			case 1:
				return conflict.issues.isEmpty() ? "OK" : conflict.issues.stream().map(Objects::toString).collect(joining(" • "));
			case 2:
				return conflict.source;
			case 3:
				return conflict.destination;
			}

			return null;
		}

	}

	private static class FileRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			File f = (File) value;

			super.getTableCellRendererComponent(table, f.getName(), isSelected, hasFocus, row, column);
			setToolTipText(f.getPath());

			return this;
		}
	}

	private static class OpenListener extends MouseAdapter {

		@Override
		public void mouseClicked(MouseEvent evt) {
			if (evt.getClickCount() == 2) {
				JTable table = (JTable) evt.getSource();
				int row = table.getSelectedRow();
				if (row >= 0) {
					ConflictTableModel m = (ConflictTableModel) table.getModel();
					Conflict c = m.getData().get(row);

					List<File> files = Stream.of(c.source, c.destination).filter(File::exists).distinct().collect(toList());
					UserFiles.revealFiles(files);
				}
			}
		}
	}

	public static boolean check(Component parent, Map<File, File> renameMap) {
		List<Conflict> conflicts = new ArrayList<Conflict>();

		// sanity checks
		Set<File> destFiles = new HashSet<File>();

		renameMap.forEach((from, to) -> {
			List<Object> issues = new ArrayList<Object>();
			boolean override = false;

			// resolve relative paths
			to = resolve(from, to);

			// output files must have a valid file extension
			if (getExtension(to) == null && to.isFile()) {
				issues.add("Missing extension");
			}

			// one file per unique output path
			if (!destFiles.add(to)) {
				issues.add("Duplicate destination path");
			}

			// check if input and output overlap
			if (renameMap.containsKey(to) && !to.equals(from)) {
				issues.add("Conflict with source path");
			}

			// check if destination path already exists
			if (to.exists() && !to.equals(from)) {
				issues.add("File already exists");

				// allow override if this is the only issue
				override = issues.size() == 1;
			}

			if (issues.size() > 0) {
				conflicts.add(new Conflict(from, to, issues, override));
			}
		});

		if (conflicts.isEmpty()) {
			return true;
		}

		ConflictDialog dialog = new ConflictDialog(getWindow(parent), conflicts);
		dialog.setLocation(getOffsetLocation(dialog.getOwner()));
		dialog.setVisible(true);

		if (dialog.cancel()) {
			return false;
		}

		// exclude conflicts from rename map
		for (Conflict it : dialog.getConflicts()) {
			renameMap.remove(it.source);
		}
		return true;
	}

}
