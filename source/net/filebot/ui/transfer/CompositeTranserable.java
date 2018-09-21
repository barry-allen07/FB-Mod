
package net.filebot.ui.transfer;


import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;


public class CompositeTranserable implements Transferable {

	private final Transferable[] transferables;

	private final DataFlavor[] flavors;


	public CompositeTranserable(Transferable... transferables) {
		this.transferables = transferables;

		Collection<DataFlavor> flavors = new ArrayList<DataFlavor>();

		for (Transferable transferable : transferables) {
			Collections.addAll(flavors, transferable.getTransferDataFlavors());
		}

		this.flavors = flavors.toArray(new DataFlavor[0]);
	}


	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		Transferable transferable = getTransferable(flavor);

		if (transferable == null)
			return null;

		return transferable.getTransferData(flavor);
	}


	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}


	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return getTransferable(flavor) != null;
	}


	protected Transferable getTransferable(DataFlavor flavor) {
		for (Transferable transferable : transferables) {
			if (transferable.isDataFlavorSupported(flavor))
				return transferable;
		}

		return null;
	}

}
