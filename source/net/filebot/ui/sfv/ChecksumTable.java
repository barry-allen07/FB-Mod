
package net.filebot.ui.sfv;

import static net.filebot.hash.VerificationUtilities.*;

import java.awt.Color;
import java.awt.event.MouseEvent;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import net.filebot.util.ui.SwingUI.DragDropRowTableUI;

class ChecksumTable extends JTable {

	public ChecksumTable() {
		setFillsViewportHeight(true);
		setAutoCreateRowSorter(true);
		setAutoCreateColumnsFromModel(true);
		setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);

		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		setRowHeight(20);

		setDragEnabled(true);
		setUI(new DragDropRowTableUI());

		// force white background (e.g. gtk-laf default table background is gray)
		setBackground(Color.WHITE);
		setGridColor(Color.LIGHT_GRAY);

		// highlight CRC32 patterns in filenames in green and with smaller font-size
		setDefaultRenderer(String.class, new HighlightPatternCellRenderer(EMBEDDED_CHECKSUM));
		setDefaultRenderer(ChecksumRow.State.class, new StateIconCellRenderer());
		setDefaultRenderer(ChecksumCell.class, new ChecksumCellRenderer());
	}

	@Override
	protected ChecksumTableModel createDefaultDataModel() {
		return new ChecksumTableModel();
	}

	@Override
	protected JTableHeader createDefaultTableHeader() {
		return new JTableHeader(columnModel) {

			@Override
			public String getToolTipText(MouseEvent evt) {
				try {
					int columnIndex = columnModel.getColumnIndexAtX(evt.getX());
					int modelIndex = columnModel.getColumn(columnIndex).getModelIndex();

					// display column root of checksum column
					return getModel().getColumnRoot(modelIndex).getPath();
				} catch (Exception e) {
					// ignore, column is not a checksum column
					return null;
				}
			};
		};
	}

	@Override
	public ChecksumTableModel getModel() {
		return (ChecksumTableModel) super.getModel();
	}

	@Override
	public void createDefaultColumnsFromModel() {
		super.createDefaultColumnsFromModel();

		for (int i = 0; i < getColumnCount(); i++) {
			TableColumn column = getColumnModel().getColumn(i);

			if (i == 0) {
				column.setPreferredWidth(45);
			} else if (i == 1) {
				column.setPreferredWidth(400);
			} else if (i >= 2) {
				column.setPreferredWidth(150);
			}
		}
	}

}
