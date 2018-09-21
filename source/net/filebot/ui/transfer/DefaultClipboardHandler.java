package net.filebot.ui.transfer;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import net.filebot.util.StringUtilities;

public class DefaultClipboardHandler implements ClipboardHandler {

	@Override
	public void exportToClipboard(JComponent component, Clipboard clip, int action) throws IllegalStateException {
		clip.setContents(new StringSelection(export(component)), null);
	}

	protected String export(JComponent component) {
		if (component instanceof JList) {
			return export((JList) component);
		}
		if (component instanceof JTree) {
			return export((JTree) component);
		}
		if (component instanceof JTable) {
			return export((JTable) component);
		}
		throw new IllegalArgumentException("JComponent not supported: " + component);
	}

	protected String export(Stream<?> values) {
		return StringUtilities.join(values, System.lineSeparator());
	}

	protected String export(JList list) {
		return export(list.getSelectedValuesList().stream());
	}

	protected String export(JTree tree) {
		return export(stream(tree.getSelectionPaths()).map(TreePath::getLastPathComponent));
	}

	protected String export(JTable table) {
		return export(stream(table.getSelectedRows()).map(row -> table.getRowSorter().convertRowIndexToModel(row)).mapToObj(row -> {
			return IntStream.range(0, table.getColumnCount()).mapToObj(column -> {
				return table.getModel().getValueAt(row, column);
			}).map(v -> Objects.toString(v, "")).collect(joining("\t"));
		}));
	}
}
