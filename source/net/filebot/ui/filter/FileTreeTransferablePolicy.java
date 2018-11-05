package net.filebot.ui.filter;

import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.ExceptionUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import javax.swing.tree.TreeNode;

import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.ui.filter.FileTree.FileNode;
import net.filebot.ui.filter.FileTree.FolderNode;
import net.filebot.ui.transfer.BackgroundFileTransferablePolicy;
import net.filebot.util.FastFile;

class FileTreeTransferablePolicy extends BackgroundFileTransferablePolicy<TreeNode> {

	private final FileTree tree;

	public FileTreeTransferablePolicy(FileTree tree) {
		this.tree = tree;
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	protected void clear() {
		super.clear();
		tree.clear();
	}

	@Override
	protected void process(List<TreeNode> root) {
		tree.getModel().setRoot(new FolderNode(root));
		tree.getModel().reload();
	}

	@Override
	protected void process(Exception e) {
		log.log(Level.WARNING, getRootCauseMessage(e), e);
	}

	@Override
	protected void load(List<File> files, TransferAction action) {
		// make sure we have access to the parent folder structure, not just the dropped file
		if (isMacSandbox()) {
			MacAppUtilities.askUnlockFolders(getWindow(tree), files);
		}

		try {
			// use fast file to minimize system calls like length(), isDirectory(), isFile(), ...
			TreeNode[] node = files.stream().map(FastFile::new).map(this::getTreeNode).toArray(TreeNode[]::new);

			// publish on EDT
			publish(node);
		} catch (CancellationException e) {
			// supposed to happen if background execution was aborted
		}
	}

	private TreeNode getTreeNode(File file) {
		if (Thread.interrupted()) {
			throw new CancellationException();
		}

		if (file.isDirectory()) {
			LinkedList<TreeNode> children = new LinkedList<TreeNode>();

			for (File f : getChildren(file, NOT_HIDDEN, HUMAN_NAME_ORDER)) {
				if (f.isDirectory()) {
					children.addFirst(getTreeNode(f));
				} else {
					children.addLast(getTreeNode(f));
				}
			}

			return new FolderNode(file, getFolderName(file), new ArrayList<TreeNode>(children));
		}

		return new FileNode(file);
	}

	@Override
	public String getFileFilterDescription() {
		return "Folders";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return null;
	}

}
