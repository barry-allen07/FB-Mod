
package net.filebot.ui.transfer;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

public abstract class TextFileExportHandler implements TransferableExportHandler, FileExportHandler {

	@Override
	public abstract boolean canExport();

	public abstract void export(PrintWriter out);

	@Override
	public abstract String getDefaultFileName();

	@Override
	public void export(File file) throws IOException {
		try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
			export(out);
		}
	}

	public String export() {
		StringWriter buffer = new StringWriter();
		try (PrintWriter out = new PrintWriter(buffer)) {
			export(out);
		}
		return buffer.toString();
	}

	@Override
	public int getSourceActions(JComponent c) {
		return canExport() ? TransferHandler.COPY_OR_MOVE : TransferHandler.NONE;
	}

	@Override
	public Transferable createTransferable(JComponent c) {
		return new TextFileTransferable(getDefaultFileName(), export());
	}

	@Override
	public void exportDone(JComponent source, Transferable data, int action) {

	}

}
