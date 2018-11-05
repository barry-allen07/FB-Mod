
package net.filebot.ui.transfer;


import java.awt.datatransfer.Clipboard;

import javax.swing.JComponent;


public interface ClipboardHandler {

	public void exportToClipboard(JComponent comp, Clipboard clip, int action) throws IllegalStateException;

}
