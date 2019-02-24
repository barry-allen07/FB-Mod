package net.filebot.ui.rename;

import static java.util.Arrays.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.UserFiles.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import javax.script.ScriptException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import net.filebot.ResourceManager;
import net.filebot.format.ExpressionFormat;
import net.filebot.format.MediaBindingBean;
import net.filebot.mediainfo.MediaInfo;
import net.filebot.mediainfo.MediaInfo.StreamKind;
import net.filebot.util.DefaultThreadFactory;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.miginfocom.swing.MigLayout;

class BindingDialog extends JDialog {

	private final JTextField infoTextField = new JTextField();
	private final JTextField mediaFileTextField = new JTextField();

	private final Format infoObjectFormat;
	private final BindingTableModel bindingModel = new BindingTableModel();

	private MediaBindingBean sample = null;

	private boolean submit = false;

	public BindingDialog(Window owner, String title, Format infoObjectFormat, boolean editable) {
		super(owner, title, ModalityType.DOCUMENT_MODAL);
		this.infoObjectFormat = infoObjectFormat;

		JComponent root = (JComponent) getContentPane();
		root.setLayout(new MigLayout("nogrid, novisualpadding, fill, insets dialog", "", "[pref!]paragraph[pref!]2px[grow,fill]paragraph[pref!]"));

		// decorative tabbed pane
		JTabbedPane inputContainer = new JTabbedPane();
		inputContainer.setFocusable(false);

		JPanel inputPanel = new JPanel(new MigLayout("nogrid, fill"));
		inputPanel.setOpaque(false);

		inputPanel.add(new JLabel("Info Object:"), "wrap 2px");
		inputPanel.add(infoTextField, "hmin 20px, growx, wrap paragraph");

		inputPanel.add(new JLabel("Media File:"), "wrap 2px");
		inputPanel.add(mediaFileTextField, "hmin 20px, growx");
		inputPanel.add(createImageButton(mediaInfoAction), "gap rel, w 28px!, h 28px!");
		inputPanel.add(createImageButton(selectFileAction), "gap rel, w 28px!, h 28px!, wrap paragraph");
		inputContainer.add("Bindings", inputPanel);

		root.add(inputContainer, "growx, wrap");
		root.add(new JLabel("Preview:"), "gap 5px, wrap");
		root.add(new JScrollPane(createBindingTable(bindingModel)), "grow, growprio 200, wrap");

		if (editable) {
			root.add(newButton("Use Bindings", ResourceManager.getIcon("dialog.continue"), evt -> finish(true)), "tag apply");
			root.add(newButton("Cancel", ResourceManager.getIcon("dialog.cancel"), evt -> finish(false)), "tag cancel");
		} else {
			root.add(newButton("Close", ResourceManager.getIcon("dialog.continue"), e -> finish(false)), "tag ok");
		}

		// disabled by default
		infoTextField.setEditable(false);
		mediaFileTextField.setEditable(false);

		mediaInfoAction.setEnabled(false);
		selectFileAction.setEnabled(editable);

		// finish dialog and close window manually
		addWindowListener(windowClosed(evt -> finish(false)));

		// initialize window properties
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setSize(420, 520);
	}

	private JTable createBindingTable(TableModel model) {
		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);
		table.setBackground(Color.white);

		table.setDefaultRenderer(Future.class, new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column);

				Future<String> future = (Future<String>) value;

				// reset state
				setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

				try {
					// try to get result
					setText(future.get(0, TimeUnit.MILLISECONDS));
				} catch (TimeoutException e) {
					// not ready yet
					setText("Pending …");

					// highlight cell
					if (!isSelected) {
						setForeground(new Color(0x6495ED)); // CornflowerBlue
					}
				} catch (Exception e) {
					// could not evaluate expression
					setText("undefined");

					// highlight cell
					if (!isSelected) {
						setForeground(Color.gray);
					}
				}

				return this;
			}
		});

		return table;
	}

	public boolean submit() {
		return submit;
	}

	private void finish(boolean submit) {
		// cancel background evaluators
		this.submit = submit;
		this.bindingModel.executor.shutdownNow();

		setVisible(false);
		dispose();
	}

	public void setSample(MediaBindingBean sample) {
		this.sample = sample;

		Object i = getInfoObject();
		if (i != null) {
			infoTextField.setText(infoObjectFormat.format(i));
			infoTextField.setToolTipText("<html><pre>" + escapeHTML(json(i, true)) + "</pre></html>");
			infoTextField.setEnabled(true);
		} else {
			infoTextField.setText("none");
			infoTextField.setToolTipText("null");
			infoTextField.setEnabled(false);
		}

		File f = getMediaFile();
		if (f != null) {
			mediaFileTextField.setText(f.getPath());
			mediaFileTextField.setEnabled(true);
			mediaInfoAction.setEnabled(true);
		} else {
			mediaFileTextField.setText("none");
			mediaFileTextField.setEnabled(false);
			mediaInfoAction.setEnabled(false);
		}

		updatePreviewModel();
	}

	public MediaBindingBean getSample() {
		return sample;
	}

	public Object getInfoObject() {
		return sample == null ? null : sample.getInfoObject();
	}

	public File getMediaFile() {
		return sample == null ? null : sample.getFileObject();
	}

	private void updatePreviewModel() {
		// ignore lazy events that come in after the window has been closed
		if (sample == null || bindingModel.executor.isShutdown()) {
			return;
		}

		String[] expressions = COMMA.split(ResourceBundle.getBundle(getClass().getName()).getString("expressions"));
		bindingModel.setModel(asList(expressions), sample);
	}

	protected final Action mediaInfoAction = new AbstractAction("Open MediaInfo", ResourceManager.getIcon("action.properties")) {

		private Map<StreamKind, List<Map<String, String>>> getMediaInfo(File file) {
			try {
				return MediaInfo.snapshot(file);
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
				return null;
			}
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			Map<StreamKind, List<Map<String, String>>> mediaInfo = getMediaInfo(getMediaFile());

			// check if we could get some info
			if (mediaInfo == null)
				return;

			// create table tab for each stream
			JTabbedPane tabbedPane = new JTabbedPane();

			ResourceBundle bundle = ResourceBundle.getBundle(BindingDialog.class.getName());
			RowFilter<Object, Object> excludeRowFilter = RowFilter.notFilter(RowFilter.regexFilter(bundle.getString("parameter.exclude")));

			for (StreamKind streamKind : mediaInfo.keySet()) {
				for (Map<String, String> parameters : mediaInfo.get(streamKind)) {
					JPanel panel = new JPanel(new MigLayout("fill"));
					panel.setOpaque(false);

					JTable table = new JTable(new ParameterTableModel(parameters));
					table.setAutoCreateRowSorter(true);
					table.setAutoCreateColumnsFromModel(true);
					table.setFillsViewportHeight(true);

					table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
					table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

					table.setBackground(Color.white);
					table.setGridColor(new Color(0xEEEEEE));
					table.setRowHeight(25);

					// set media info exclude filter
					TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
					sorter.setRowFilter(excludeRowFilter);

					panel.add(new JScrollPane(table), "grow");
					tabbedPane.addTab(streamKind.toString(), panel);
				}
			}

			// show media info dialog
			JDialog dialog = new JDialog(getWindow(evt.getSource()), "MediaInfo", ModalityType.DOCUMENT_MODAL);

			JComponent c = (JComponent) dialog.getContentPane();
			c.setLayout(new MigLayout("fill, novisualpadding", "[align center]", "[fill][pref!]"));
			c.add(tabbedPane, "grow, wrap");
			c.add(newButton("OK", e -> dialog.setVisible(false)), "w 80px!, h 25px!");

			dialog.pack();
			dialog.setLocationRelativeTo(BindingDialog.this);

			dialog.setVisible(true);
		}

	};

	protected final Action selectFileAction = new AbstractAction("Select Media File", ResourceManager.getIcon("action.load")) {

		@Override
		public void actionPerformed(ActionEvent evt) {
			ExtensionFileFilter mediaFiles = ExtensionFileFilter.union(VIDEO_FILES, AUDIO_FILES, SUBTITLE_FILES, IMAGE_FILES);
			List<File> selection = showLoadDialogSelectFiles(false, false, getMediaFile(), mediaFiles, (String) getValue(NAME), evt);

			if (selection.size() > 0) {
				// update text field
				File file = selection.get(0).getAbsoluteFile();
				Object info = xattr.getMetaInfo(file);

				if (info == null || infoObjectFormat.format(info) == null) {
					info = getInfoObject();
				}

				setSample(new MediaBindingBean(info, file));
			}
		}
	};

	private static class Evaluator extends SwingWorker<String, Void> {

		private final String expression;
		private final Object bindingBean;

		private Evaluator(String expression, Object bindingBean) {
			this.expression = expression;
			this.bindingBean = bindingBean;
		}

		public String getExpression() {
			return expression;
		}

		@Override
		protected String doInBackground() throws Exception {
			ExpressionFormat format = new ExpressionFormat(expression) {

				@Override
				protected Object[] compile(String expression) throws ScriptException {
					// simple expression format, everything as one expression
					return new Object[] { compileScriptlet(expression) };
				}
			};

			// evaluate expression with given bindings
			return format.format(bindingBean);
		}

		@Override
		public String toString() {
			try {
				return get(0, TimeUnit.SECONDS);
			} catch (Exception e) {
				return null;
			}
		}
	}

	private static class BindingTableModel extends AbstractTableModel {

		private final List<Evaluator> model = new ArrayList<Evaluator>();

		private final ExecutorService executor = Executors.newFixedThreadPool(1, new DefaultThreadFactory("Evaluator", Thread.MIN_PRIORITY));

		public void setModel(Collection<String> expressions, Object bindingBean) {
			// cancel old workers and clear model
			clear();

			for (String expression : expressions) {
				Evaluator evaluator = new Evaluator(expression, bindingBean) {

					@Override
					protected void done() {
						// update cell when computation is complete
						fireTableCellUpdated(this);
					}
				};

				// enqueue for background execution
				executor.execute(evaluator);

				model.add(evaluator);
			}

			// update view
			fireTableDataChanged();
		}

		public void clear() {
			for (Evaluator evaluator : model) {
				evaluator.cancel(true);
			}

			model.clear();

			// update view
			fireTableDataChanged();
		}

		public void fireTableCellUpdated(Evaluator element) {
			int index = model.indexOf(element);

			if (index >= 0) {
				fireTableCellUpdated(index, 1);
			}
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Expression";
			case 1:
				return "Value";
			default:
				return null;
			}
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public int getRowCount() {
			return model.size();
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return String.class;
			case 1:
				return Future.class;
			default:
				return null;
			}
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return model.get(row).getExpression();
			case 1:
				return model.get(row);
			default:
				return null;
			}
		}
	}

	private static class ParameterTableModel extends AbstractTableModel {

		private final List<Entry<?, ?>> data;

		public ParameterTableModel(Map<?, ?> data) {
			this.data = new ArrayList<Entry<?, ?>>(data.entrySet());
		}

		@Override
		public int getRowCount() {
			return data.size();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Parameter";
			case 1:
				return "Value";
			default:
				return null;
			}
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return data.get(row).getKey();
			case 1:
				return data.get(row).getValue();
			default:
				return null;
			}
		}
	}

}
