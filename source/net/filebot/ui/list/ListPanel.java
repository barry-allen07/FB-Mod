package net.filebot.ui.list;

import static java.util.stream.Collectors.*;
import static javax.swing.BorderFactory.*;
import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.IntStream;

import javax.script.ScriptException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.TransferHandler;
import javax.swing.border.Border;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.google.common.eventbus.Subscribe;

import net.filebot.ResourceManager;
import net.filebot.format.ExpressionFormat;
import net.filebot.ui.FileBotList;
import net.filebot.ui.FileBotListExportHandler;
import net.filebot.ui.PanelBuilder;
import net.filebot.ui.rename.FormatExpressionTextArea;
import net.filebot.ui.transfer.LoadAction;
import net.filebot.ui.transfer.SaveAction;
import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.ui.transfer.TransferablePolicy.TransferAction;
import net.filebot.util.ui.DefaultFancyListCellRenderer;
import net.filebot.util.ui.PrototypeCellValueUpdater;
import net.filebot.util.ui.SwingEventBus;
import net.miginfocom.swing.MigLayout;

public class ListPanel extends JComponent {

	public static final String DEFAULT_SEQUENCE_FORMAT = "Sequence - {i.pad(2)}";
	public static final String DEFAULT_FILE_FORMAT = "{fn}";
	public static final String DEFAULT_EPISODE_FORMAT = "{n} - {s00e00} - [{absolute}] - [{airdate}] - {t}";

	private FormatExpressionTextArea editor = new FormatExpressionTextArea(new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_GROOVY));
	private SpinnerNumberModel fromSpinnerModel = new SpinnerNumberModel(1, 0, Integer.MAX_VALUE, 1);
	private SpinnerNumberModel toSpinnerModel = new SpinnerNumberModel(20, 0, Integer.MAX_VALUE, 1);

	private FileBotList<ListItem> list = new FileBotList<ListItem>();

	public ListPanel() {
		list.setTitle("Title");

		// need a fixed cell size for high performance scrolling
		list.getListComponent().setFixedCellHeight(28);
		list.getListComponent().getModel().addListDataListener(new PrototypeCellValueUpdater(list.getListComponent(), ""));

		list.getRemoveAction().setEnabled(true);
		list.setTransferablePolicy(new FileListTransferablePolicy(list::setTitle, this::setFormatTemplate, this::createItemSequence));

		FileBotListExportHandler<ListItem> exportHandler = new FileBotListExportHandler<ListItem>(list, (item, out) -> out.println(item.getFormattedValue()));
		list.setExportHandler(exportHandler);
		list.getTransferHandler().setClipboardHandler(exportHandler);

		// XXX The user interface of your app is not consistent with the macOS Human Interface Guidelines. Specifically: We found that menu items are not visible, except by right-clicking (see screenshot). See the "WYSIWYG (What You See Is What You Get)," "Give Users
		// Alternate Ways to Accomplish Tasks," and "Designing Contextual Menus" sections of the Human Interface Guidelines.
		if (!isMacSandbox()) {
			JPopupMenu popup = new JPopupMenu("List");
			JMenu menu = new JMenu("Send to");
			for (PanelBuilder panel : PanelBuilder.textHandlerSequence()) {
				menu.add(newAction(panel.getName(), panel.getIcon(), evt -> {
					String text = list.getExportHandler().export();
					SwingEventBus.getInstance().post(panel);
					invokeLater(200, () -> SwingEventBus.getInstance().post(new StringSelection(text)));
				}));
			}
			popup.add(menu);
			popup.addSeparator();
			popup.add(newAction("Copy", ResourceManager.getIcon("rename.action.copy"), evt -> {
				list.getTransferHandler().getClipboardHandler().exportToClipboard(this, Toolkit.getDefaultToolkit().getSystemClipboard(), TransferHandler.COPY);
			}));
			popup.add(new SaveAction(list.getExportHandler()));
			list.getListComponent().setComponentPopupMenu(popup);
		}

		// cell renderer
		list.getListComponent().setCellRenderer(new DefaultFancyListCellRenderer() {

			@Override
			protected void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.configureListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				ListItem item = (ListItem) value;
				Object object = item.getFormattedValue(); // format just-in-time

				if (object instanceof Exception) {
					Exception error = (Exception) object;
					setText(error.getMessage());
					setIcon(ResourceManager.getIcon("status.warning"));
				} else {
					setText(object.toString());
					setIcon(null);
				}
			}
		});

		JSpinner fromSpinner = new JSpinner(fromSpinnerModel);
		JSpinner toSpinner = new JSpinner(toSpinnerModel);

		fromSpinner.setEditor(new NumberEditor(fromSpinner, "#"));
		toSpinner.setEditor(new NumberEditor(toSpinner, "#"));

		RTextScrollPane editorScrollPane = new RTextScrollPane(editor, false);
		editorScrollPane.setLineNumbersEnabled(false);
		editorScrollPane.setFoldIndicatorEnabled(false);
		editorScrollPane.setIconRowHeaderEnabled(false);

		editorScrollPane.setVerticalScrollBarPolicy(RTextScrollPane.VERTICAL_SCROLLBAR_NEVER);
		editorScrollPane.setHorizontalScrollBarPolicy(RTextScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		editorScrollPane.setBackground(editor.getBackground());
		editorScrollPane.setOpaque(true);

		Border defaultBorder = new JTextField().getBorder();
		Border okBorder = createCompoundBorder(defaultBorder, createEmptyBorder(2, 2, 2, 2));
		Border errorBorder = createCompoundBorder(createLineBorder(Color.RED, 1), createCompoundBorder(defaultBorder, createEmptyBorder(1, 1, 1, 1)));

		editorScrollPane.setBorder(okBorder);

		// update format on change
		editor.onChange(20, evt -> {
			try {
				String expression = editor.getText().trim();
				setFormat(expression.isEmpty() ? null : new ExpressionFormat(expression));
				editorScrollPane.setBorder(okBorder);
			} catch (ScriptException e) {
				editorScrollPane.setBorder(errorBorder);
			}
		});

		setLayout(new MigLayout("nogrid, fill, insets dialog", "align center", "[pref!, center][fill]"));

		add(new JLabel("Pattern:"), "gapbefore indent");
		add(editorScrollPane, "gap related, growx, wmin 2cm, h pref!, sizegroupy editor");
		add(new JLabel("From:"), "gap 5mm");
		add(fromSpinner, "gap related, wmax 15mm, sizegroup spinner, sizegroupy editor");
		add(new JLabel("To:"), "gap 5mm");
		add(toSpinner, "gap related, wmax 15mm, sizegroup spinner, sizegroupy editor");
		add(newButton("Sequence", ResourceManager.getIcon("action.export"), evt -> createItemSequence()), "gap 7mm, gapafter indent, wrap paragraph");

		add(list, "grow");

		// panel with buttons that will be added inside the list component
		JPanel buttonPanel = new JPanel(new MigLayout("insets 1.2mm, nogrid, fill", "align center"));
		buttonPanel.add(new JButton(new LoadAction(list::getTransferablePolicy)));
		buttonPanel.add(new JButton(new SaveAction(list.getExportHandler())), "gap related");

		list.add(buttonPanel, BorderLayout.SOUTH);

		// initialize with default values
		createItemSequence();
	}

	private ExpressionFormat format;
	private String template;

	public ListItem createItem(Object object, int i, int from, int to, List<?> context) {
		return new ListItem(new IndexedBindingBean(object, i, from, to, context), format);
	}

	public void setFormat(ExpressionFormat format) {
		this.format = format;

		// update items
		for (ListIterator<ListItem> itr = list.getModel().listIterator(); itr.hasNext();) {
			itr.set(new ListItem(itr.next().getBindings(), format));
		}
	}

	public void createItemSequence(List<?> objects) {
		List<ListItem> items = IntStream.range(0, objects.size()).mapToObj(i -> createItem(objects.get(i), i + 1, 1, objects.size(), objects)).collect(toList());

		list.getListComponent().clearSelection();
		list.getModel().clear();
		list.getModel().addAll(items);
	}

	public void createItemSequence() {
		int from = fromSpinnerModel.getNumber().intValue();
		int to = toSpinnerModel.getNumber().intValue();

		List<Integer> context = IntStream.rangeClosed(from, to).boxed().collect(toList());
		List<ListItem> items = context.stream().map(it -> createItem(it, it.intValue(), from, to, context)).collect(toList());

		setFormatTemplate(DEFAULT_SEQUENCE_FORMAT);
		list.setTitle("Sequence");
		list.getListComponent().clearSelection();
		list.getModel().clear();
		list.getModel().addAll(items);
	}

	public void setFormatTemplate(String format) {
		if (template != format) {
			template = format;
			editor.setText(format);
		}
	}

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		TransferablePolicy handler = list.getTransferablePolicy();

		if (handler != null && handler.accept(transferable)) {
			handler.handleTransferable(transferable, TransferAction.PUT);
		}
	}

}
