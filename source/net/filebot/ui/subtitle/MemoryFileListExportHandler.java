package net.filebot.ui.subtitle;

import static java.util.stream.Collectors.*;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

import net.filebot.ui.transfer.ByteBufferTransferable;
import net.filebot.ui.transfer.ClipboardHandler;
import net.filebot.ui.transfer.TransferableExportHandler;
import net.filebot.vfs.MemoryFile;

class MemoryFileListExportHandler implements TransferableExportHandler, ClipboardHandler {

	public boolean canExport(JComponent component) {
		JList<?> list = (JList<?>) component;

		// can't export anything, if nothing is selected
		return !list.isSelectionEmpty();
	}

	public List<MemoryFile> export(JComponent component) {
		JList<?> list = (JList<?>) component;

		// get selected values as list
		return list.getSelectedValuesList().stream().map(MemoryFile.class::cast).collect(toList());
	}

	@Override
	public int getSourceActions(JComponent component) {
		return canExport(component) ? TransferHandler.COPY_OR_MOVE : TransferHandler.NONE;
	}

	@Override
	public Transferable createTransferable(JComponent component) {
		Map<String, ByteBuffer> vfs = new HashMap<String, ByteBuffer>();

		for (MemoryFile file : export(component)) {
			vfs.put(file.getName(), file.getData());
		}

		return new ByteBufferTransferable(vfs);
	}

	@Override
	public void exportToClipboard(JComponent component, Clipboard clip, int action) {
		clip.setContents(createTransferable(component), null);
	}

	@Override
	public void exportDone(JComponent source, Transferable data, int action) {

	}

}
