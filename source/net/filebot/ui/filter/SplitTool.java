package net.filebot.ui.filter;

import static java.util.Collections.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

import net.filebot.ui.filter.FileTree.FolderNode;
import net.filebot.ui.transfer.DefaultTransferHandler;
import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.LoadingOverlayPane;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.miginfocom.swing.MigLayout;

class SplitTool extends Tool<TreeModel> {

	private FileTree tree = new FileTree();

	private SpinnerNumberModel spinnerModel = new SpinnerNumberModel(4480, 0, Integer.MAX_VALUE, 100);

	public SplitTool() {
		super("Parts");

		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setBorder(new SeparatorBorder(2, new Color(0, 0, 0, 90), GradientStyle.TOP_TO_BOTTOM, SeparatorBorder.Position.BOTTOM));

		JSpinner spinner = new JSpinner(spinnerModel);
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));

		setLayout(new MigLayout("insets 0, nogrid, fill", "align center", "[fill][pref!]"));

		add(new LoadingOverlayPane(treeScrollPane, this), "grow, wrap");

		add(new JLabel("Split every"));
		add(spinner, "wmax 80, gap top rel, gap bottom unrel");
		add(new JLabel("MB"));

		tree.setTransferHandler(new DefaultTransferHandler(null, new FileTreeExportHandler()));
		tree.setDragEnabled(true);

		// update model in foreground, will be much faster than the initial load because length() is cached now
		spinnerModel.addChangeListener(evt -> {
			List<File> root = getRoot();
			if (root.size() > 0) {
				setRoot(root);
			}
		});
	}

	private long getSplitSize() {
		return spinnerModel.getNumber().intValue() * ONE_MEGABYTE;
	}

	@Override
	protected TreeModel createModelInBackground(List<File> root) {
		if (root.isEmpty()) {
			return new DefaultTreeModel(new FolderNode("Volumes", emptyList()));
		}

		int nextPart = 1;
		long splitSize = getSplitSize();

		List<File> files = listFiles(root, FILES, HUMAN_NAME_ORDER);

		List<TreeNode> rootGroup = new ArrayList<TreeNode>();
		List<File> currentPart = new ArrayList<File>();
		List<File> remainder = new ArrayList<File>();
		long totalSize = 0;

		for (File f : files) {
			long fileSize = f.length();

			if (fileSize > splitSize) {
				remainder.add(f);
				continue;
			}

			if (totalSize + fileSize > splitSize) {
				// part is full, add node and start with next one
				rootGroup.add(createStatisticsNode(nextPart++, currentPart));

				// reset total size and file list
				totalSize = 0;
				currentPart.clear();
			}

			totalSize += fileSize;
			currentPart.add(f);

			if (Thread.interrupted()) {
				throw new CancellationException();
			}
		}

		if (!currentPart.isEmpty()) {
			// add last part
			rootGroup.add(createStatisticsNode(nextPart++, currentPart));
		}

		if (!remainder.isEmpty()) {
			rootGroup.add(createStatisticsNode("Remainder", remainder));
		}

		return new DefaultTreeModel(new FolderNode("Volumes", rootGroup));
	}

	protected FolderNode createStatisticsNode(int disk, List<File> files) {
		return createStatisticsNode(String.format("Disk %,d", disk), files);
	}

	@Override
	protected void setModel(TreeModel model) {
		tree.setModel(model);
	}

}
