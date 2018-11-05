package net.filebot.ui.filter;

import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;

import com.google.common.eventbus.Subscribe;

import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.ui.transfer.TransferablePolicy.TransferAction;
import net.miginfocom.swing.MigLayout;

public class FilterPanel extends JComponent {

	private final FileTreePanel fileTreePanel = new FileTreePanel();
	private final JTabbedPane toolsPanel = new JTabbedPane();

	public FilterPanel() {
		setLayout(new MigLayout("insets dialog, gapx 50, fill, nogrid"));
		add(fileTreePanel, "grow 1, w 300:pref:500");
		add(toolsPanel, "grow 2");

		fileTreePanel.addPropertyChangeListener(FileTreePanel.FILE_TREE_PROPERTY, evt -> {
			// stopped loading, refresh tools
			for (int i = 0; i < toolsPanel.getTabCount(); i++) {
				Tool<?> tool = (Tool<?>) toolsPanel.getComponentAt(i);
				tool.setRoot(fileTreePanel.getFileTree().getRoot());
			}
		});
	}

	public void addTool(Tool<?> tool) {
		toolsPanel.addTab(tool.getName(), tool);
	}

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		TransferablePolicy handler = fileTreePanel.getTransferablePolicy();

		if (handler != null && handler.accept(transferable)) {
			handler.handleTransferable(transferable, TransferAction.PUT);
		}
	}

}
