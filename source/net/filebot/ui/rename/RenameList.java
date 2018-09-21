package net.filebot.ui.rename;

import static java.util.Collections.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import ca.odell.glazedlists.EventList;
import net.filebot.ResourceManager;
import net.filebot.ui.FileBotList;
import net.filebot.ui.transfer.LoadAction;
import net.filebot.util.ui.ActionPopup;
import net.filebot.util.ui.PrototypeCellValueUpdater;
import net.miginfocom.swing.MigLayout;

class RenameList<E> extends FileBotList<E> {

	private JPanel buttonPanel;

	public RenameList(EventList<E> model) {
		// replace default model with given model
		setModel(model);

		// disable multi-selection for the sake of simplicity
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// need a fixed cell size for high performance scrolling
		list.setFixedCellHeight(28);
		list.getModel().addListDataListener(new PrototypeCellValueUpdater(list, ""));

		list.addMouseListener(dndReorderMouseAdapter);
		list.addMouseMotionListener(dndReorderMouseAdapter);

		getRemoveAction().setEnabled(true);

		buttonPanel = new JPanel(new MigLayout("insets 1.2mm, nogrid, novisualpadding, fill", "align center"));
		buttonPanel.add(createImageButton(downAction), "sgy button");
		buttonPanel.add(createImageButton(upAction), "gap 0, sgy button");
		buttonPanel.add(createLoadButton(), "gap 10px, sgy button");

		add(buttonPanel, BorderLayout.SOUTH);

		listScrollPane.getViewport().setBackground(list.getBackground());
	}

	public JPanel getButtonPanel() {
		return buttonPanel;
	}

	private JButton createLoadButton() {
		ActionPopup actionPopup = new ActionPopup("Load Files", ResourceManager.getIcon("action.load"));

		actionPopup.add(newAction("Select Folders", ResourceManager.getIcon("tree.closed"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), 0));
		}));

		actionPopup.add(newAction("Select Files", ResourceManager.getIcon("file.generic"), evt -> {
			loadAction.actionPerformed(new ActionEvent(evt.getSource(), evt.getID(), evt.getActionCommand(), ActionEvent.SHIFT_MASK));
		}));

		JButton button = new JButton(loadAction);
		button.setComponentPopupMenu(actionPopup);
		return button;
	}

	private final LoadAction loadAction = new LoadAction(this::getTransferablePolicy);

	private final Action upAction = newAction("Align Up", ResourceManager.getIcon("action.up"), evt -> {
		int index = getListComponent().getSelectedIndex();

		if (index > 0) {
			swap(model, index, index - 1);
			getListComponent().setSelectedIndex(index - 1);
		}
	});

	private final Action downAction = newAction("Align Down", ResourceManager.getIcon("action.down"), evt -> {
		int index = getListComponent().getSelectedIndex();

		if (index < model.size() - 1) {
			swap(model, index, index + 1);
			getListComponent().setSelectedIndex(index + 1);
		}
	});

	private final MouseAdapter dndReorderMouseAdapter = new MouseAdapter() {

		private int lastIndex = -1;

		@Override
		public void mousePressed(MouseEvent m) {
			lastIndex = getListComponent().getSelectedIndex();
		}

		@Override
		public void mouseDragged(MouseEvent m) {
			int currentIndex = getListComponent().getSelectedIndex();

			if (currentIndex != lastIndex && lastIndex >= 0 && currentIndex >= 0) {
				swap(model, lastIndex, currentIndex);
				lastIndex = currentIndex;
			}
		}
	};

}
