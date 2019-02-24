
package net.filebot.ui.sfv;


import static java.awt.Font.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.io.FileNotFoundException;

import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.util.ExceptionUtilities;


public class ChecksumCellRenderer extends DefaultTableCellRenderer {

	private final SwingWorkerCellRenderer progressRenderer = new SwingWorkerCellRenderer();

	private final Color verificationForeground = new Color(0x009900);


	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		boolean pendingWorker = false;

		if (value instanceof SwingWorker) {
			if (((SwingWorker<?, ?>) value).getState() != StateValue.PENDING)
				return progressRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			pendingWorker = true;
		}

		// ignore focus
		super.getTableCellRendererComponent(table, value, isSelected, false, row, column);

		// check row state for ERROR
		boolean isError = (table.getValueAt(row, 0) == ChecksumRow.State.ERROR);

		// if row state is ERROR and if we are not selected use text color RED,
		// else use default table colors
		setForeground(isSelected ? table.getSelectionForeground() : isError ? Color.RED : isVerificationColumn(table, column) ? verificationForeground : table.getForeground());
		setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

		// use BOLD font on ERROR
		setFont(getFont().deriveFont(isError ? BOLD : PLAIN));

		if (pendingWorker) {
			setText("Pending...");
		} else if (value == null && !isSelected) {
			setBackground(derive(table.getGridColor(), 0.1f));
		} else if (value instanceof FileNotFoundException) {
			setText("File not found");
		} else if (value instanceof Throwable) {
			setText(ExceptionUtilities.getRootCauseMessage((Throwable) value));
		}

		return this;
	}


	private boolean isVerificationColumn(JTable table, int column) {
		ChecksumTableModel model = (ChecksumTableModel) table.getModel();
		int modelColumn = table.getColumnModel().getColumn(column).getModelIndex();

		return model.isVerificationColumn(modelColumn);
	}

}
