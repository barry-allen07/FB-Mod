
package net.filebot.ui.sfv;


import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;


class SwingWorkerCellRenderer extends JPanel implements TableCellRenderer {

	private final JProgressBar progressBar = new JProgressBar(0, 100);


	public SwingWorkerCellRenderer() {
		super(new BorderLayout());

		// set margin for progress bar on parent component,
		// because setting it on the progress bar itself does not work (border size is not respected in the paint method)
		setBorder(new EmptyBorder(2, 2, 2, 2));

		progressBar.setStringPainted(true);

		add(progressBar, BorderLayout.CENTER);
	}


	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

		progressBar.setValue(((SwingWorker<?, ?>) value).getProgress());

		return this;
	}


	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(long tm, int x, int y, int width, int height) {
	}


	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(Rectangle r) {
	}


	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint() {
	}


	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
	}

}
