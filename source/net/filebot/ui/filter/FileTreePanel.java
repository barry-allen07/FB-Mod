package net.filebot.ui.filter;

import static javax.swing.BorderFactory.*;
import static net.filebot.ui.transfer.BackgroundFileTransferablePolicy.*;
import static net.filebot.util.ui.SwingUI.*;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

import net.filebot.ResourceManager;
import net.filebot.ui.transfer.DefaultTransferHandler;
import net.filebot.ui.transfer.LoadAction;
import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.util.ui.LoadingOverlayPane;
import net.miginfocom.swing.MigLayout;

class FileTreePanel extends JComponent {

	private FileTree fileTree = new FileTree();

	private FileTreeTransferablePolicy transferablePolicy = new FileTreeTransferablePolicy(fileTree);

	public FileTreePanel() {
		fileTree.setTransferHandler(new DefaultTransferHandler(transferablePolicy, null));

		setBorder(createTitledBorder("File Tree"));

		setLayout(new MigLayout("insets 0, nogrid, fill", "align center", "[fill][pref!]"));
		add(new LoadingOverlayPane(new JScrollPane(fileTree), this), "grow, wrap 1.2mm");
		add(new JButton(loadAction));
		add(new JButton(clearAction), "gap 1.2mm, wrap 1.2mm");

		// forward loading events
		transferablePolicy.addPropertyChangeListener(evt -> {
			if (LOADING_PROPERTY.equals(evt.getPropertyName())) {
				firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
			}
		});

		// update tree when loading is finished
		transferablePolicy.addPropertyChangeListener(evt -> {
			if (LOADING_PROPERTY.equals(evt.getPropertyName()) && !(Boolean) evt.getNewValue()) {
				fireFileTreeChange();
			}
		});
	}

	public FileTree getFileTree() {
		return fileTree;
	}

	public TransferablePolicy getTransferablePolicy() {
		return transferablePolicy;
	}

	private final LoadAction loadAction = new LoadAction(this::getTransferablePolicy);

	private final Action clearAction = newAction("Clear", ResourceManager.getIcon("action.clear"), evt -> {
		transferablePolicy.reset();
		fileTree.clear();
		fireFileTreeChange();
	});

	public static final String FILE_TREE_PROPERTY = "FILE_TREE";

	private void fireFileTreeChange() {
		firePropertyChange(FILE_TREE_PROPERTY, null, fileTree);
	}

}
