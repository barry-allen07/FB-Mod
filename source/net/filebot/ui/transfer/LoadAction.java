package net.filebot.ui.transfer;

import static net.filebot.Logging.*;
import static net.filebot.UserFiles.*;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import net.filebot.ResourceManager;
import net.filebot.ui.transfer.TransferablePolicy.TransferAction;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

public class LoadAction extends AbstractAction {

	public final Supplier<TransferablePolicy> handler;

	public LoadAction(Supplier<TransferablePolicy> handler) {
		this("Load", ResourceManager.getIcon("action.load"), handler);
	}

	public LoadAction(String name, Icon icon, Supplier<TransferablePolicy> handler) {
		super(name, icon);
		this.handler = handler;
	}

	public TransferAction getTransferAction(ActionEvent evt) {
		// if CTRL was pressed when the button was clicked, assume ADD action (same as with dnd)
		return ((evt.getModifiers() & ActionEvent.CTRL_MASK) != 0) ? TransferAction.ADD : TransferAction.PUT;
	}

	protected File getDefaultFile() {
		return null;
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		try {
			// get transferable policy from action properties
			TransferablePolicy transferablePolicy = handler.get();
			if (transferablePolicy == null) {
				return;
			}

			List<File> files = showLoadDialogSelectFiles(true, true, getDefaultFile(), getFileFilter(transferablePolicy), (String) getValue(Action.NAME), evt);
			if (files.isEmpty()) {
				return;
			}

			FileTransferable transferable = new FileTransferable(files);
			if (transferablePolicy.accept(transferable)) {
				transferablePolicy.handleTransferable(transferable, getTransferAction(evt));
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e.toString(), e);
		}
	}

	protected ExtensionFileFilter getFileFilter(TransferablePolicy transferablePolicy) {
		if (transferablePolicy instanceof FileTransferablePolicy) {
			final FileTransferablePolicy ftp = ((FileTransferablePolicy) transferablePolicy);
			if (ftp.getFileFilterDescription() != null && ftp.getFileFilterExtensions() != null) {
				return new ExtensionFileFilter(ftp.getFileFilterExtensions()) {
					@Override
					public String toString() {
						return ftp.getFileFilterDescription();
					};
				};
			}
		}
		return null;
	}
}
