
package net.filebot.ui;

import java.awt.Cursor;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.BiConsumer;

import javax.swing.JComponent;

import net.filebot.ui.transfer.ClipboardHandler;
import net.filebot.ui.transfer.TextFileExportHandler;

public class FileBotListExportHandler<T> extends TextFileExportHandler implements ClipboardHandler {

	protected final FileBotList<T> list;
	protected final BiConsumer<T, PrintWriter> exportItem;

	public FileBotListExportHandler(FileBotList<T> list) {
		this(list, (item, out) -> out.println(item));
	}

	public FileBotListExportHandler(FileBotList<T> list, BiConsumer<T, PrintWriter> exportItem) {
		this.list = list;
		this.exportItem = exportItem;
	}

	@Override
	public boolean canExport() {
		return list.getModel().size() > 0;
	}

	@Override
	public void export(PrintWriter out) {
		try {
			list.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			for (T item : list.getModel()) {
				exportItem.accept(item, out);
			}
		} finally {
			list.setCursor(Cursor.getDefaultCursor());
		}
	}

	@Override
	public String getDefaultFileName() {
		return list.getTitle() + ".txt";
	}

	@Override
	public void exportToClipboard(JComponent c, Clipboard clip, int action) throws IllegalStateException {
		StringWriter buffer = new StringWriter();
		try (PrintWriter out = new PrintWriter(buffer)) {
			list.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			for (T item : list.getListComponent().getSelectedValuesList()) {
				exportItem.accept(item, out);
			}
		} finally {
			list.setCursor(Cursor.getDefaultCursor());
		}

		clip.setContents(new StringSelection(buffer.toString()), null);
	}

}
