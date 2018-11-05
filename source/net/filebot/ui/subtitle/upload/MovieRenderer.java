package net.filebot.ui.subtitle.upload;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.web.Movie;

class MovieRenderer extends DefaultTableCellRenderer {

	private Icon icon;

	public MovieRenderer(Icon icon) {
		this.icon = icon;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		if (value != null) {
			Movie movie = (Movie) value;
			setText(movie.toString());
			setToolTipText(String.format("%s [tt%07d]", movie.toString(), movie.getImdbId()));
			setIcon(icon);
			setForeground(table.getForeground());
		} else {
			setText("<Click to select movie / series>");
			setToolTipText(null);
			setIcon(null);
			setForeground(Color.LIGHT_GRAY);
		}

		return this;
	}
}