package net.filebot.ui.filter;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.filebot.ResourceManager;
import net.filebot.UserFiles;
import net.filebot.ui.PanelBuilder;
import net.filebot.ui.transfer.FileTransferable;
import net.filebot.util.FilterIterator;
import net.filebot.util.TreeIterator;
import net.filebot.util.ui.SwingEventBus;

public class FileTree extends JTree {

	public FileTree() {
		super(new DefaultTreeModel(new FolderNode()));
		getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		setCellRenderer(new FileTreeCellRenderer());
		setShowsRootHandles(true);
		setRootVisible(false);

		setRowHeight(22);
		setLargeModel(true);

		addMouseListener(new ExpandCollapsePopupListener());
	}

	@Override
	public DefaultTreeModel getModel() {
		return (DefaultTreeModel) super.getModel();
	}

	public List<File> getRoot() {
		FolderNode model = (FolderNode) getModel().getRoot();

		return model.getChildren().stream().map(node -> {
			if (node instanceof FolderNode) {
				FolderNode folder = (FolderNode) node;
				return folder.getFile();
			}
			if (node instanceof FileNode) {
				FileNode file = (FileNode) node;
				return file.getFile();
			}
			return null;
		}).collect(toList());
	}

	public void clear() {
		getModel().setRoot(new FolderNode());
		getModel().reload();
	}

	public void expandAll() {
		for (int row = 0; row < getRowCount(); row++) {
			expandRow(row);
		}
	}

	public void collapseAll() {
		for (int row = 0; row < getRowCount(); row++) {
			collapseRow(row);
		}
	}

	private class OpenExpandCollapsePopup extends JPopupMenu {

		public OpenExpandCollapsePopup() {
			Collection<File> selectedFiles = getFiles(getSelectionPaths());

			if (selectedFiles != null && !selectedFiles.isEmpty()) {
				JMenu menu = new JMenu("Send to");
				for (PanelBuilder panel : PanelBuilder.fileHandlerSequence()) {
					menu.add(new JMenuItem(new ImportAction(panel, selectedFiles)));
				}

				add(menu);
				addSeparator();
			}

			if (selectedFiles.size() > 0) {
				add(new JMenuItem(new RevealAction("Reveal", selectedFiles)));
				add(new RevealAction("Reveal Folder", selectedFiles.stream().map(File::getParentFile).distinct().collect(toList())));
				addSeparator();
			}

			add(newAction("Expand all", ResourceManager.getIcon("tree.expand"), evt -> expandAll()));
			add(newAction("Collapse all", ResourceManager.getIcon("tree.collapse"), evt -> collapseAll()));
		}

		private Collection<File> getFiles(TreePath[] selection) {
			if (selection == null || selection.length == 0) {
				return emptySet();
			}

			Set<File> files = new LinkedHashSet<File>();
			for (TreePath path : selection) {
				collectFiles(path.getLastPathComponent(), files);
			}
			return files;
		}

		private void collectFiles(Object node, Collection<File> files) {
			if (node instanceof FileNode) {
				files.add(((FileNode) node).getFile());
			} else if (node instanceof FolderNode) {
				for (Object it : ((FolderNode) node).getChildren()) {
					collectFiles(it, files);
				}
			}
		}

		private class RevealAction extends AbstractAction {

			private Collection<File> files;

			public RevealAction(String text, Collection<File> files) {
				super(text);
				this.files = files;
			}

			@Override
			public void actionPerformed(ActionEvent event) {
				UserFiles.revealFiles(files);
			}
		}

		private class ImportAction extends AbstractAction {

			private PanelBuilder panel;
			private Collection<File> files;

			public ImportAction(PanelBuilder panel, Collection<File> files) {
				super(panel.getName(), panel.getIcon());
				this.panel = panel;
				this.files = files;
			}

			@Override
			public void actionPerformed(ActionEvent event) {
				// switch to panel
				SwingEventBus.getInstance().post(panel);

				// load files
				invokeLater(200, () -> SwingEventBus.getInstance().post(new FileTransferable(files)));
			}
		}
	}

	private class ExpandCollapsePopupListener extends MouseAdapter {

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
				TreePath path = getPathForLocation(e.getX(), e.getY());

				if (!getSelectionModel().isPathSelected(path)) {
					// if clicked node is not selected, set selection to this node (and deselect all other currently selected nodes)
					setSelectionPath(path);
				}

				OpenExpandCollapsePopup popup = new OpenExpandCollapsePopup();
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		}
	}

	public static class AbstractTreeNode implements TreeNode {

		private TreeNode parent;

		@Override
		public TreeNode getParent() {
			return parent;
		}

		public void setParent(TreeNode parent) {
			this.parent = parent;
		}

		@Override
		public Enumeration<? extends TreeNode> children() {
			return null;
		}

		@Override
		public boolean getAllowsChildren() {
			return false;
		}

		@Override
		public TreeNode getChildAt(int childIndex) {
			return null;
		}

		@Override
		public int getChildCount() {
			return 0;
		}

		@Override
		public int getIndex(TreeNode node) {
			return -1;
		}

		@Override
		public boolean isLeaf() {
			// if we have no children, tell the UI we are a leaf,
			// so that it won't display any good-for-nothing expand buttons
			return getChildCount() == 0;
		}

	}

	public static class FileNode extends AbstractTreeNode {

		private final File file;

		public FileNode(File file) {
			this.file = file;
		}

		public File getFile() {
			return file;
		}

		@Override
		public String toString() {
			return file.getName();
		}
	}

	public static class FolderNode extends AbstractTreeNode {

		private final File file;

		private final String title;
		private final List<TreeNode> children;

		public FolderNode() {
			this(emptyList()); // empty root node
		}

		public FolderNode(List<TreeNode> children) {
			this(null, "/", children); // root node
		}

		public FolderNode(String title, List<TreeNode> children) {
			this(null, title, children); // virtual node
		}

		public FolderNode(File file, String title, List<TreeNode> children) {
			this.file = file;
			this.title = title;
			this.children = children;
		}

		public File getFile() {
			return file;
		}

		@Override
		public String toString() {
			return title;
		}

		public List<TreeNode> getChildren() {
			return children;
		}

		@Override
		public Enumeration<? extends TreeNode> children() {
			return enumeration(children);
		}

		@Override
		public boolean getAllowsChildren() {
			return true;
		}

		@Override
		public TreeNode getChildAt(int childIndex) {
			return children.get(childIndex);
		}

		@Override
		public int getChildCount() {
			return children.size();
		}

		@Override
		public int getIndex(TreeNode node) {
			return children.indexOf(node);
		}

		public Iterator<TreeNode> treeIterator() {
			return new TreeIterator<TreeNode>(this) {

				@Override
				protected Iterator<TreeNode> children(TreeNode node) {
					if (node instanceof FolderNode) {
						return ((FolderNode) node).getChildren().iterator();
					}

					// can't step into non-folder nodes
					return null;
				}

			};
		}

		public Iterator<File> fileIterator() {
			return new FilterIterator<TreeNode, File>(treeIterator()) {

				@Override
				protected File filter(TreeNode node) {
					if (node instanceof FileNode) {
						return ((FileNode) node).getFile();
					}

					// filter out non-file nodes
					return null;
				}
			};
		}
	}

}
