package net.filebot.ui.transfer;

import static net.filebot.Settings.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.filebot.util.FileUtilities;
import net.filebot.util.TemporaryFolder;

public class ByteBufferTransferable implements Transferable {

	protected final Map<String, ByteBuffer> vfs;

	private FileTransferable transferable;

	public ByteBufferTransferable(Map<String, ByteBuffer> vfs) {
		this.vfs = vfs;
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (FileTransferable.isFileListFlavor(flavor)) {
			try {
				// create file for transfer on demand
				if (transferable == null) {
					transferable = createFileTransferable();
				}

				return transferable.getTransferData(flavor);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		throw new UnsupportedFlavorException(flavor);
	}

	protected FileTransferable createFileTransferable() throws IOException {
		// remove invalid characters from file name
		List<File> files = new ArrayList<File>();

		for (Entry<String, ByteBuffer> entry : vfs.entrySet()) {
			String name = entry.getKey();
			ByteBuffer data = entry.getValue().duplicate();

			// write temporary file
			files.add(createTemporaryFile(name, data));
		}

		return new FileTransferable(files);
	}

	protected File createTemporaryFile(String name, ByteBuffer data) throws IOException {
		// remove invalid characters from file name
		String validFileName = validateFileName(name);

		// create new temporary file in TEMP/APP_NAME [UUID]/dnd
		File temporaryFile = TemporaryFolder.getFolder(getApplicationName()).subFolder("dnd").createFile(validFileName);

		// write data to file
		FileUtilities.writeFile(data, temporaryFile);

		return temporaryFile;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.javaFileListFlavor, FileTransferable.uriListFlavor };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return FileTransferable.isFileListFlavor(flavor);
	}

}
