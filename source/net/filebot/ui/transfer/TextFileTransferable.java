package net.filebot.ui.transfer;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.Set;

public class TextFileTransferable extends ByteBufferTransferable {

	private final String text;

	public TextFileTransferable(String name, String text) {
		this(name, text, UTF_8);
	}

	public TextFileTransferable(String name, String text, Charset charset) {
		// lazy data map for file transfer
		super(new AbstractMap<String, ByteBuffer>() {

			@Override
			public Set<Entry<String, ByteBuffer>> entrySet() {
				return singletonMap(name, charset.encode(text)).entrySet();
			}
		});

		// text transfer
		this.text = text;
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		// check file flavor first, because text/uri-list is also text flavor
		if (super.isDataFlavorSupported(flavor)) {
			return super.getTransferData(flavor);
		}

		// check text flavor
		if (flavor.isFlavorTextType()) {
			return text;
		}

		throw new UnsupportedFlavorException(flavor);
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.javaFileListFlavor, FileTransferable.uriListFlavor, DataFlavor.stringFlavor };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		// file flavor or text flavor
		return super.isDataFlavorSupported(flavor) || flavor.isFlavorTextType();
	}

}
