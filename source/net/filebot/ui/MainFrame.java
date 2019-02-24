package net.filebot.ui;

import static java.awt.event.InputEvent.*;
import static java.awt.event.KeyEvent.*;
import static java.util.Arrays.*;
import static java.util.Comparator.*;
import static javax.swing.BorderFactory.*;
import static javax.swing.KeyStroke.*;
import static javax.swing.ScrollPaneConstants.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.google.common.eventbus.Subscribe;

import net.filebot.CacheManager;
import net.filebot.LicenseModel;
import net.filebot.Settings;
import net.filebot.cli.GroovyPad;
import net.filebot.util.PreferencesMap.PreferencesEntry;
import net.filebot.util.ui.DefaultFancyListCellRenderer;
import net.filebot.util.ui.ShadowBorder;
import net.filebot.util.ui.SwingEventBus;
import net.miginfocom.swing.MigLayout;

public class MainFrame extends JFrame {

	private static final PreferencesEntry<String> persistentSelectedPanel = Settings.forPackage(MainFrame.class).entry("panel.selected").defaultValue("0");

	private JList selectionList;
	private HeaderPanel headerPanel;

	public MainFrame(PanelBuilder[] panels) {
		super(getWindowTitle());

		selectionList = new PanelSelectionList(panels);
		headerPanel = new HeaderPanel();

		JScrollPane selectionListScrollPane = new JScrollPane(selectionList, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER);
		selectionListScrollPane.setOpaque(false);
		selectionListScrollPane.setBorder(createCompoundBorder(new ShadowBorder(), isMacApp() ? createLineBorder(new Color(0x809DB8), 1, false) : selectionListScrollPane.getBorder()));

		headerPanel.getTitleLabel().setBorder(createEmptyBorder(8, 90, 10, 0));

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets 0, fill, hidemode 3", String.format("%dpx[fill]", isUbuntuApp() ? 110 : 95), "fill"));

		c.add(selectionListScrollPane, "pos 6px 10px n 100%-12px");
		c.add(headerPanel, "growx, dock north");

		// restore selected panel
		try {
			selectionList.setSelectedIndex(Integer.parseInt(persistentSelectedPanel.getValue()));
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		// show initial panel
		try {
			showPanel((PanelBuilder) selectionList.getSelectedValue());
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		selectionList.addListSelectionListener(evt -> {
			showPanel((PanelBuilder) selectionList.getSelectedValue());

			if (!evt.getValueIsAdjusting()) {
				persistentSelectedPanel.setValue(Integer.toString(selectionList.getSelectedIndex()));
			}
		});

		setSize(1060, 650);
		setMinimumSize(new Dimension(900, 340));

		// KEYBOARD SHORTCUTS
		installAction(getRootPane(), getKeyStroke(VK_DELETE, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), newAction("Clear Cache", evt -> {
			withWaitCursor(getRootPane(), () -> {
				CacheManager.getInstance().clearAll();
				log.info("Cache has been cleared");
			});
		}));

		installAction(getRootPane(), getKeyStroke(VK_F5, 0), newAction("Run", evt -> {
			withWaitCursor(getRootPane(), () -> {
				GroovyPad pad = new GroovyPad();

				pad.addWindowListener(new WindowAdapter() {

					@Override
					public void windowOpened(WindowEvent e) {
						setVisible(false);
					};

					@Override
					public void windowClosing(WindowEvent e) {
						setVisible(true);
					};
				});

				pad.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
				pad.setModalExclusionType(ModalExclusionType.TOOLKIT_EXCLUDE);
				pad.setLocationByPlatform(true);
				pad.setVisible(true);
			});
		}));

		installAction(this.getRootPane(), getKeyStroke(VK_F1, 0), newAction("Help", evt -> openURI(getEmbeddedHelpURL())));

		SwingEventBus.getInstance().register(this);
	}

	@Subscribe
	public void updateLicense(LicenseModel licence) {
		try {
			licence.check();
			setTitle(getWindowTitle());
		} catch (Throwable e) {
			setTitle(String.format("%s (%s)", getWindowTitle(), e.getMessage()));
		}
	}

	@Subscribe
	public void selectPanel(PanelBuilder panel) {
		selectionList.setSelectedValue(panel, false);
	}

	private void showPanel(PanelBuilder selectedBuilder) {
		if (selectedBuilder == null)
			return;

		JComponent contentPane = (JComponent) getContentPane();
		JComponent selectedPanel = null;

		for (int i = 0; i < contentPane.getComponentCount(); i++) {
			JComponent panel = (JComponent) contentPane.getComponent(i);
			PanelBuilder builder = (PanelBuilder) panel.getClientProperty(PanelBuilder.class.getName());
			if (builder != null) {
				if (builder.equals(selectedBuilder)) {
					selectedPanel = panel;
				} else if (panel.isVisible()) {
					panel.setVisible(false);
					SwingEventBus.getInstance().unregister(panel);
				}
			}
		}

		if (selectedPanel == null) {
			selectedPanel = selectedBuilder.create();
			selectedPanel.setVisible(false); // invisible by default
			selectedPanel.putClientProperty(PanelBuilder.class.getName(), selectedBuilder);
			contentPane.add(selectedPanel);
		}

		// make visible, ignore action is visible already
		if (!selectedPanel.isVisible()) {
			headerPanel.setTitle(selectedBuilder.getName());
			selectedPanel.setVisible(true);
			SwingEventBus.getInstance().register(selectedPanel);
		}
	}

	private static class PanelSelectionList extends JList {

		private static final int SELECTDELAY_ON_DRAG_OVER = 300;

		public PanelSelectionList(PanelBuilder[] builders) {
			super(builders);

			setCellRenderer(new PanelCellRenderer());
			setPrototypeCellValue(stream(builders).max(comparingInt(p -> p.getName().length())).get());

			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			setBorder(createEmptyBorder(4, 5, 4, 5));

			// disable default copy & paste support
			setTransferHandler(null);

			// initialize "drag over" panel selection
			new DropTarget(this, new DragDropListener());
		}

		private class DragDropListener extends DropTargetAdapter {

			private boolean selectEnabled = false;

			private Timer dragEnterTimer;

			@Override
			public void dragOver(DropTargetDragEvent dtde) {
				if (selectEnabled) {
					int index = locationToIndex(dtde.getLocation());
					setSelectedIndex(index);
				}
			}

			@Override
			public void dragEnter(final DropTargetDragEvent dtde) {
				dragEnterTimer = invokeLater(SELECTDELAY_ON_DRAG_OVER, () -> {
					selectEnabled = true;

					// bring window to front when drag-and-drop operation is in progress
					if (Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
						Desktop.getDesktop().requestForeground(true);
					} else {
						SwingUtilities.getWindowAncestor(((DropTarget) dtde.getSource()).getComponent()).toFront();
					}
				});
			}

			@Override
			public void dragExit(DropTargetEvent dte) {
				selectEnabled = false;

				if (dragEnterTimer != null) {
					dragEnterTimer.stop();
				}
			}

			@Override
			public void drop(DropTargetDropEvent dtde) {

			}

		}

	}

	private static class PanelCellRenderer extends DefaultFancyListCellRenderer {

		public PanelCellRenderer() {
			super(10, 0, new Color(0x163264));

			// center labels in list
			setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

			setHighlightingEnabled(false);

			setVerticalTextPosition(SwingConstants.BOTTOM);
			setHorizontalTextPosition(SwingConstants.CENTER);
		}

		@Override
		public void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.configureListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			PanelBuilder panel = (PanelBuilder) value;
			setText(panel.getName());
			setIcon(panel.getIcon());
		}

	}

}
