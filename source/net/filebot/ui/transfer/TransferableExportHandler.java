
package net.filebot.ui.transfer;


import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;


public interface TransferableExportHandler {

	public Transferable createTransferable(JComponent c);


	public int getSourceActions(JComponent c);


	public void exportDone(JComponent source, Transferable data, int action);

}
