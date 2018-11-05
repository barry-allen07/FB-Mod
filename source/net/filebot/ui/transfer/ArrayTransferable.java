package net.filebot.ui.transfer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.lang.reflect.Array;

public class ArrayTransferable<T> implements Transferable {

	public static DataFlavor flavor(Class<?> componentType) {
		return new DataFlavor(Array.newInstance(componentType, 0).getClass(), "Array");
	}

	private final T[] array;

	public ArrayTransferable(T[] array) {
		this.array = array;
	}

	public T[] getArray() {
		return array.clone();
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		if (isDataFlavorSupported(flavor)) {
			return getArray();
		}

		return null;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { new DataFlavor(array.getClass(), "Array") };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return array.getClass().equals(flavor.getRepresentationClass());
	}

}
