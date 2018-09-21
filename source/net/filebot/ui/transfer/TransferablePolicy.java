package net.filebot.ui.transfer;

import static net.filebot.Logging.*;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.InvalidDnDOperationException;
import java.util.logging.Level;

import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;

public abstract class TransferablePolicy {

	public abstract boolean accept(Transferable tr) throws Exception;

	public abstract void handleTransferable(Transferable tr, TransferAction action) throws Exception;

	public boolean canImport(TransferSupport support) {
		if (support.isDrop()) {
			support.setShowDropLocation(false);
		}

		try {
			return accept(support.getTransferable());
		} catch (InvalidDnDOperationException e) {
			// final drop may cause this exception because, the transfer data can only be accessed
			// *after* the drop has been accepted, but canImport is called before that

			// just assume that the transferable will be accepted, accept will be called in importData again anyway
			return true;
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
			return false;
		}
	}

	public boolean importData(TransferSupport support) {
		Transferable transferable = support.getTransferable();

		try {
			if (accept(transferable)) {
				handleTransferable(transferable, getTransferAction(support));
				return true;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
		}

		// transferable was not accepted, or transfer failed
		return false;
	}

	protected TransferAction getTransferAction(TransferSupport support) {
		if (support.isDrop()) {
			return TransferAction.fromDnDConstant(support.getDropAction());
		}

		// use ADD by default (e.g. clipboard transfers)
		return TransferAction.ADD;
	}

	public static enum TransferAction {
		PUT(TransferHandler.MOVE), ADD(TransferHandler.COPY), LINK(TransferHandler.LINK);

		private final int dndConstant;

		private TransferAction(int dndConstant) {
			this.dndConstant = dndConstant;
		}

		public int getDnDConstant() {
			return dndConstant;
		}

		public static TransferAction fromDnDConstant(int dndConstant) {
			for (TransferAction action : values()) {
				if (dndConstant == action.dndConstant)
					return action;
			}

			throw new IllegalArgumentException("Unsupported dndConstant: " + dndConstant);
		}

	}

}
