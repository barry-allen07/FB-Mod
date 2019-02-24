package net.filebot.ui.filter;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;

import net.filebot.ResourceManager;
import net.filebot.util.ui.FancyTreeCellRenderer;
import net.filebot.util.ui.GradientStyle;

class FileTreeCellRenderer extends FancyTreeCellRenderer {

	public FileTreeCellRenderer() {
		super(GradientStyle.TOP_TO_BOTTOM);

		openIcon = ResourceManager.getIcon("tree.open");
		closedIcon = ResourceManager.getIcon("tree.closed");
		leafIcon = ResourceManager.getIcon("file.generic");
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
		if (leaf && isFolder(value)) {
			// make leafs that are empty folders look like expanded nodes
			expanded = true;
			leaf = false;
		}

		super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

		return this;
	}

	private boolean isFolder(Object value) {
		if (((TreeNode) value).getAllowsChildren())
			return true;

		return false;
	}
}
