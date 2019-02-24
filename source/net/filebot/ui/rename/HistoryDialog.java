package net.filebot.ui.rename;

import static java.awt.Font.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.regex.Pattern.*;
import static java.util.stream.Collectors.*;
import static javax.swing.JOptionPane.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.UserFiles.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import net.filebot.History;
import net.filebot.History.Element;
import net.filebot.History.Sequence;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.HistorySpooler;
import net.filebot.ResourceManager;
import net.filebot.StandardRenameAction;
import net.filebot.ui.transfer.FileExportHandler;
import net.filebot.ui.transfer.FileTransferablePolicy;
import net.filebot.ui.transfer.LoadAction;
import net.filebot.ui.transfer.SaveAction;
import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.LazyDocumentListener;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.filebot.util.ui.notification.SeparatorBorder.Position;
import net.miginfocom.swing.MigLayout;

class HistoryDialog extends JDialog {

	private final JLabel infoLabel = new JLabel();

	private final JTextField filterEditor = new JTextField();

	private final SequenceTableModel sequenceModel = new SequenceTableModel();

	private final ElementTableModel elementModel = new ElementTableModel();

	private final JTable sequenceTable = createTable(sequenceModel);

	private final JTable elementTable = createTable(elementModel);

	public HistoryDialog(Window owner) {
		super(owner, "History", ModalityType.DOCUMENT_MODAL);

		// bold title label in header
		JLabel title = new JLabel(this.getTitle());
		title.setFont(title.getFont().deriveFont(BOLD));

		JPanel header = new JPanel(new MigLayout("insets dialog, nogrid, fillx"));

		header.setBackground(Color.white);
		header.setBorder(new SeparatorBorder(1, new Color(0xB4B4B4), new Color(0xACACAC), GradientStyle.LEFT_TO_RIGHT, Position.BOTTOM));

		header.add(title, "wrap");
		header.add(infoLabel, "gap indent*2, wrap");

		JPanel content = new JPanel(new MigLayout("fill, insets dialog, nogrid, novisualpadding", "", "[pref!][150px:pref:200px][200px:pref:max, grow][pref!]"));

		content.add(new JLabel("Filter:"), "gap indent:push");
		content.add(filterEditor, "wmin 120px, gap rel");
		content.add(createImageButton(clearFilterAction), "w pref!, h pref!, gap right indent, wrap");

		content.add(createScrollPaneGroup("Sequences", sequenceTable), "growx, wrap paragraph");
		content.add(createScrollPaneGroup("Elements", elementTable), "growx, wrap paragraph");

		Action importAction = new LoadAction("Import", ResourceManager.getIcon("action.load"), this::getTransferablePolicy);

		content.add(new JButton(importAction), "wmin button, hmin 25px, gap indent, sg button");
		content.add(new JButton(new SaveAction("Export", ResourceManager.getIcon("action.save"), exportHandler)), "gap rel, sg button");
		content.add(new JButton(new RevertCurrentSelectionAction()), "gap left unrel:push, sgy button");
		content.add(new JButton(closeAction), "gap left unrel, gap right indent, sg button");

		JComponent pane = (JComponent) getContentPane();
		pane.setLayout(new MigLayout("fill, insets 0, nogrid"));

		pane.add(header, "h min!, growx, dock north");
		pane.add(content, "grow");

		// initialize selection modes
		sequenceTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		elementTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// bind element model to selected sequence
		sequenceTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting())
					return;

				if (sequenceTable.getSelectedRow() >= 0) {
					List<Element> elements = new ArrayList<Element>();
					for (int row : sequenceTable.getSelectedRows()) {
						elements.addAll(sequenceModel.getRow(sequenceTable.convertRowIndexToModel(row)).elements());
					}
					elementModel.setData(elements);
				}
			}
		});

		// clear sequence selection when elements are selected
		elementTable.getSelectionModel().addListSelectionListener(evt -> {
			if (elementTable.getSelectedRow() >= 0) {
				// allow selected rows only in one of the two tables
				sequenceTable.getSelectionModel().clearSelection();
			}
		});

		// sort by number descending
		sequenceTable.getRowSorter().setSortKeys(singletonList(new SortKey(0, SortOrder.DESCENDING)));

		// change date format
		sequenceTable.setDefaultRenderer(Date.class, new DefaultTableCellRenderer() {

			private final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				return super.getTableCellRendererComponent(table, format.format(value), isSelected, hasFocus, row, column);
			}
		});

		// display broken status in second column
		elementTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

				// reset icon
				setIcon(null);

				if (column == 1) {
					if (elementModel.isBroken(table.convertRowIndexToModel(row))) {
						setIcon(ResourceManager.getIcon("status.link.broken"));
					} else {
						setIcon(ResourceManager.getIcon("status.link.ok"));
					}
				}

				return this;
			}
		});

		// update sequence and element filter on change
		filterEditor.getDocument().addDocumentListener(new LazyDocumentListener(evt -> {
			List<HistoryFilter> filterList = new ArrayList<HistoryFilter>();

			// filter by all words
			for (String word : SPACE.split(filterEditor.getText())) {
				filterList.add(new HistoryFilter(word));
			}

			// use filter on both tables
			for (JTable table : Arrays.asList(sequenceTable, elementTable)) {
				TableRowSorter sorter = (TableRowSorter) table.getRowSorter();
				sorter.setRowFilter(RowFilter.andFilter(filterList));
			}

			if (sequenceTable.getSelectedRow() < 0 && sequenceTable.getRowCount() > 0) {
				// selection lost, maybe due to filtering, auto-select next row
				sequenceTable.getSelectionModel().addSelectionInterval(0, 0);
			}
		}));

		// install context menu
		sequenceTable.addMouseListener(contextMenuProvider);
		elementTable.addMouseListener(contextMenuProvider);

		// initialize window properties
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLocationByPlatform(true);
		setResizable(true);
		setSize(580, 640);
	}

	public void setModel(History history) {
		// update table model
		sequenceModel.setData(history.sequences());

		if (sequenceTable.getRowCount() > 0) {
			// auto-select first element and update element table
			sequenceTable.getSelectionModel().addSelectionInterval(0, 0);
		} else {
			// clear element table
			elementModel.setData(new ArrayList<Element>(0));
		}

		// display basic statistics
		initializeInfoLabel();
	}

	public History getModel() {
		return new History(sequenceModel.getData());
	}

	public JLabel getInfoLabel() {
		return infoLabel;
	}

	private void initializeInfoLabel() {
		int count = 0;
		Date since = new Date();

		for (Sequence sequence : sequenceModel.getData()) {
			count += sequence.elements().size();

			if (sequence.date().before(since))
				since = sequence.date();
		}

		infoLabel.setText(String.format("A total of %,d files have been renamed since %s.", count, DateFormat.getDateInstance().format(since)));
	}

	private JScrollPane createScrollPaneGroup(String title, JComponent component) {
		JScrollPane scrollPane = new JScrollPane(component);
		scrollPane.setBorder(new CompoundBorder(new TitledBorder(title), scrollPane.getBorder()));

		if (isMacApp()) {
			scrollPane.setOpaque(false);
		}

		return scrollPane;
	}

	private JTable createTable(TableModel model) {
		JTable table = new JTable(model);
		table.setBackground(Color.white);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);

		// hide grid
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));

		// decrease column width for the row number columns
		DefaultTableColumnModel m = ((DefaultTableColumnModel) table.getColumnModel());
		m.getColumn(0).setMaxWidth(50);

		return table;
	}

	private final Action closeAction = new AbstractAction("Close", ResourceManager.getIcon("dialog.continue")) {

		@Override
		public void actionPerformed(ActionEvent e) {
			setVisible(false);
			dispose();
		}
	};

	private final Action clearFilterAction = new AbstractAction("Clear Filter", ResourceManager.getIcon("edit.clear")) {

		@Override
		public void actionPerformed(ActionEvent e) {
			filterEditor.setText("");
		}
	};

	private final MouseListener contextMenuProvider = new MouseAdapter() {

		@Override
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				JTable table = (JTable) e.getSource();

				int clickedRow = table.rowAtPoint(e.getPoint());

				if (clickedRow < 0) {
					// no row was clicked
					return;
				}

				if (!table.getSelectionModel().isSelectedIndex(clickedRow)) {
					// if clicked row is not selected, set selection to this row (and deselect all other currently selected row)
					table.getSelectionModel().setSelectionInterval(clickedRow, clickedRow);
				}

				List<Element> selection = new ArrayList<Element>();

				for (int i : table.getSelectedRows()) {
					int index = table.convertRowIndexToModel(i);

					if (sequenceModel == table.getModel()) {
						selection.addAll(sequenceModel.getRow(index).elements());
					} else if (elementModel == table.getModel()) {
						selection.add(elementModel.getRow(index));
					}
				}

				if (selection.size() > 0) {
					JPopupMenu menu = new JPopupMenu();
					menu.add(new RevertSelectionAction(selection));

					// display popup
					menu.show(table, e.getX(), e.getY());
				}
			}
		}
	};

	private class RevertCurrentSelectionAction extends RevertAction {

		public RevertCurrentSelectionAction() {
			super("Revert Selection", HistoryDialog.this);
		}

		@Override
		public List<Element> elements() {
			List<Element> selection = new ArrayList<Element>();

			if (sequenceTable.getSelectedRow() >= 0) {
				for (int i : sequenceTable.getSelectedRows()) {
					int index = sequenceTable.convertRowIndexToModel(i);
					selection.addAll(sequenceModel.getRow(index).elements());
				}
			} else if (elementTable.getSelectedRow() >= 0) {
				for (int i : elementTable.getSelectedRows()) {
					int index = elementTable.convertRowIndexToModel(i);
					selection.add(elementModel.getRow(index));
				}
			}

			return selection;
		}
	}

	private class RevertSelectionAction extends RevertAction {

		public static final String ELEMENTS = "elements";

		public RevertSelectionAction(Collection<Element> elements) {
			super("Revert...", HistoryDialog.this);
			putValue(ELEMENTS, elements.toArray(new Element[0]));
		}

		@Override
		public List<Element> elements() {
			return Arrays.asList((Element[]) getValue(ELEMENTS));
		}
	}

	private static abstract class RevertAction extends AbstractAction {

		public static final String PARENT = "parent";

		public RevertAction(String name, HistoryDialog parent) {
			putValue(NAME, name);
			putValue(SMALL_ICON, ResourceManager.getIcon("action.revert"));
			putValue(PARENT, parent);
		}

		public abstract List<Element> elements();

		public HistoryDialog parent() {
			return (HistoryDialog) getValue(PARENT);
		}

		private enum Option {

			Revert, ChangeDirectory, Cancel;

			@Override
			public String toString() {
				switch (this) {
				case Revert:
					return "Revert";
				case ChangeDirectory:
					return "Change Directory";
				default:
					return "Cancel";
				}
			}
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			List<Element> elements = elements();
			if (elements.isEmpty())
				return;

			// use default directory
			File directory = null;

			Option selectedOption = Option.ChangeDirectory;

			// change directory option
			while (selectedOption == Option.ChangeDirectory) {
				List<File> missingFiles = getMissingFiles(directory);

				Object message;
				int type;
				Set<Option> options;

				if (missingFiles.isEmpty()) {
					message = String.format("Are you sure you want to revert %d file(s)?", elements.size());
					type = QUESTION_MESSAGE;
					options = EnumSet.of(Option.Revert, Option.ChangeDirectory, Option.Cancel);
				} else {
					String text = "Some files are missing. Please select a different directory.";
					JList missingFilesComponent = new JList(missingFiles.toArray()) {

						@Override
						public Dimension getPreferredScrollableViewportSize() {
							// adjust component size
							return new Dimension(80, 80);
						}
					};

					missingFilesComponent.setCellRenderer(new DefaultListCellRenderer() {

						@Override
						public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
							return super.getListCellRendererComponent(list, ((File) value).getName(), index, isSelected, false);
						}
					});

					message = new Object[] { text, new JScrollPane(missingFilesComponent) };
					type = PLAIN_MESSAGE;
					options = EnumSet.of(Option.ChangeDirectory, Option.Cancel);
				}

				JOptionPane pane = new JOptionPane(message, type, YES_NO_CANCEL_OPTION, null, options.toArray(), Option.Cancel);

				// display option dialog
				pane.createDialog(parent(), "Revert").setVisible(true);

				// update selected option
				selectedOption = (Option) pane.getValue();

				// change directory option
				if (selectedOption == Option.ChangeDirectory) {
					directory = showOpenDialogSelectFolder(directory, selectedOption.toString(), evt);
				}
			}

			// rename files
			if (selectedOption == Option.Revert) {
				revert(directory, elements);
			}
		}

		private void revert(File directory, List<Element> elements) {
			Map<File, File> renamePlan = getRenameMap(directory);
			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(parent(), Stream.of(renamePlan.keySet(), renamePlan.values()).flatMap(c -> c.stream()).collect(toList()))) {
					return;
				}
			}

			int count = 0;
			try {
				for (Entry<File, File> entry : renamePlan.entrySet()) {
					File original = StandardRenameAction.revert(entry.getKey(), entry.getValue());
					count++;

					// clear xattr that may or may not exist
					xattr.clear(original);
				}
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to revert files: " + e.getMessage(), e);
			}

			JLabel status = parent().getInfoLabel();
			if (count == elements.size()) {
				status.setText(String.format("%d file(s) have been reverted.", count));
				status.setIcon(ResourceManager.getIcon("status.ok"));
			} else {
				status.setText(String.format("%d of %d file(s) have been reverted.", count, elements.size()));
				status.setIcon(ResourceManager.getIcon("status.error"));
			}

			// update view
			parent().repaint();
		}

		private Map<File, File> getRenameMap(File directory) {
			Map<File, File> renameMap = new LinkedHashMap<File, File>();

			for (Element element : elements()) {
				// use given directory or default directory
				File dir = directory != null ? directory : element.dir();

				// reverse
				File from = new File(element.to());
				File to = new File(element.from());

				// resolve against given directory or against the original base directory if the path is not absolute
				if (!from.isAbsolute())
					from = new File(dir, directory == null ? from.getPath() : from.getName());
				if (!to.isAbsolute())
					to = new File(dir, directory == null ? to.getPath() : to.getName());

				renameMap.put(from, to);
			}

			return renameMap;
		}

		private List<File> getMissingFiles(File directory) {
			List<File> missingFiles = new ArrayList<File>();

			for (File file : getRenameMap(directory).keySet()) {
				if (!file.exists())
					missingFiles.add(file);
			}

			return missingFiles;
		}
	}

	public TransferablePolicy getTransferablePolicy() {
		return importHandler;
	}

	private final FileTransferablePolicy importHandler = new FileTransferablePolicy() {

		@Override
		protected boolean accept(List<File> files) {
			return containsOnly(files, new ExtensionFileFilter("xml"));
		}

		@Override
		protected void clear() {
			// do nothing
		}

		@Override
		protected void load(List<File> files, TransferAction action) throws IOException {
			for (File file : files) {
				try {
					HistorySpooler.getInstance().append(History.importHistory(new FileInputStream(file)));
				} catch (Exception e) {
					log.log(Level.SEVERE, "Failed to import history: " + file, e);
				}
			}

			setModel(HistorySpooler.getInstance().getCompleteHistory()); // update view
		}

		@Override
		public String getFileFilterDescription() {
			return "History Files (.xml)";
		}

		@Override
		public List<String> getFileFilterExtensions() {
			return asList("xml");
		}
	};

	private final FileExportHandler exportHandler = new FileExportHandler() {

		@Override
		public boolean canExport() {
			// allow export of empty history
			return true;
		}

		@Override
		public void export(File file) throws IOException {
			History.exportHistory(getModel(), new FileOutputStream(file));
		}

		@Override
		public String getDefaultFileName() {
			return "history.xml";
		}
	};

	private static class HistoryFilter extends RowFilter<Object, Integer> {

		private final Pattern filter;

		public HistoryFilter(String filter) {
			this.filter = compile(quote(filter), CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS | CANON_EQ);
		}

		@Override
		public boolean include(Entry<?, ? extends Integer> entry) {
			// sequence model
			if (entry.getModel() instanceof SequenceTableModel) {
				SequenceTableModel model = (SequenceTableModel) entry.getModel();

				for (Element element : model.getRow(entry.getIdentifier()).elements()) {
					if (include(element))
						return true;
				}

				return false;
			}

			// element model
			if (entry.getModel() instanceof ElementTableModel) {
				ElementTableModel model = (ElementTableModel) entry.getModel();

				return include(model.getRow(entry.getIdentifier()));
			}

			// will not happen
			throw new IllegalArgumentException("Illegal model: " + entry.getModel());
		}

		private boolean include(Element element) {
			return include(element.to()) || include(element.from()) || include(element.dir().getPath());
		}

		private boolean include(String value) {
			return filter.matcher(value).find();
		}
	}

	private static class SequenceTableModel extends AbstractTableModel {

		private List<Sequence> data = emptyList();

		public void setData(List<Sequence> data) {
			this.data = new ArrayList<Sequence>(data);

			// update view
			fireTableDataChanged();
		}

		public List<Sequence> getData() {
			return unmodifiableList(data);
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "#";
			case 1:
				return "Name";
			case 2:
				return "Date";
			}
			return null;
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public int getRowCount() {
			return data.size();
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return Integer.class;
			case 1:
				return String.class;
			case 2:
				return Date.class;
			}
			return null;
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return row + 1;
			case 1:
				return getName(data.get(row));
			case 2:
				return data.get(row).date();
			}
			return null;
		}

		public Sequence getRow(int row) {
			return data.get(row);
		}

		private String getName(Sequence sequence) {
			StringBuilder sb = new StringBuilder();

			for (Element element : sequence.elements()) {
				String name = element.dir().getName();

				// append name only, if it isn't listed already
				if (sb.indexOf(name) < 0) {
					if (sb.length() > 0)
						sb.append(", ");

					sb.append(name);
				}
			}

			return sb.toString();
		}
	}

	private static class ElementTableModel extends AbstractTableModel {

		private List<Element> data = emptyList();

		public void setData(List<Element> data) {
			this.data = new ArrayList<Element>(data);

			// update view
			fireTableDataChanged();
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "#";
			case 1:
				return "New Name";
			case 2:
				return "Original Name";
			case 3:
				return "New Folder";
			case 4:
				return "Original Folder";
			}
			return null;
		}

		@Override
		public int getColumnCount() {
			return 5;
		}

		@Override
		public int getRowCount() {
			return data.size();
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return Integer.class;
			case 1:
				return String.class;
			case 2:
				return String.class;
			case 3:
				return File.class;
			case 4:
				return File.class;
			}
			return null;
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return row + 1;
			case 1:
				return new File(data.get(row).to()).getName();
			case 2:
				return data.get(row).from();
			case 3:
				return new File(data.get(row).to()).getParentFile();
			case 4:
				return data.get(row).dir();
			default:
				return null;
			}
		}

		public Element getRow(int row) {
			return data.get(row);
		}

		public boolean isBroken(int row) {
			Element element = data.get(row);

			File file = new File(element.to());

			// resolve relative path
			if (!file.isAbsolute())
				file = new File(element.dir(), file.getPath());

			return !file.exists();
		}
	}

}
