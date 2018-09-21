
package net.filebot.ui.transfer;


import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.TransferHandler;


public class DefaultTransferHandler extends TransferHandler {

	private TransferablePolicy transferablePolicy;
	private TransferableExportHandler exportHandler;
	private ClipboardHandler clipboardHandler;

	private boolean dragging = false;


	public DefaultTransferHandler(TransferablePolicy transferablePolicy, TransferableExportHandler exportHandler) {
		this(transferablePolicy, exportHandler, new DefaultClipboardHandler());
	}


	public DefaultTransferHandler(TransferablePolicy transferablePolicy, TransferableExportHandler exportHandler, ClipboardHandler clipboardHandler) {
		this.transferablePolicy = transferablePolicy;
		this.exportHandler = exportHandler;
		this.clipboardHandler = clipboardHandler;
	}


	@Override
	public boolean canImport(TransferSupport support) {
		// show "drop allowed" cursor when dragging even though drop is not allowed
		if (dragging)
			return true;

		if (transferablePolicy != null)
			return transferablePolicy.canImport(support);

		return false;
	}


	@Override
	public boolean importData(TransferSupport support) {
		if (dragging)
			return false;

		if (!canImport(support))
			return false;

		return transferablePolicy.importData(support);
	}


	@Override
	protected void exportDone(JComponent source, Transferable data, int action) {
		dragging = false;

		if (data == null)
			return;

		if (exportHandler != null)
			exportHandler.exportDone(source, data, action);
	}


	@Override
	public int getSourceActions(JComponent c) {
		if (exportHandler != null)
			return exportHandler.getSourceActions(c);

		return NONE;
	}


	@Override
	protected Transferable createTransferable(JComponent c) {
		dragging = true;

		if (exportHandler != null)
			return exportHandler.createTransferable(c);

		return null;
	}


	@Override
	public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException {
		if (clipboardHandler != null)
			clipboardHandler.exportToClipboard(comp, clip, action);
	}


	public TransferablePolicy getTransferablePolicy() {
		return transferablePolicy;
	}


	public void setTransferablePolicy(TransferablePolicy transferablePolicy) {
		this.transferablePolicy = transferablePolicy;
	}


	public TransferableExportHandler getExportHandler() {
		return exportHandler;
	}


	public void setExportHandler(TransferableExportHandler exportHandler) {
		this.exportHandler = exportHandler;
	}


	public ClipboardHandler getClipboardHandler() {
		return clipboardHandler;
	}


	public void setClipboardHandler(ClipboardHandler clipboardHandler) {
		this.clipboardHandler = clipboardHandler;
	}

}
