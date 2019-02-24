package net.filebot.ui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import net.filebot.Language;
import net.filebot.ResourceManager;
import net.filebot.util.ui.DashedSeparator;

public class LanguageComboBoxCellRenderer implements ListCellRenderer {

	private Border padding = new EmptyBorder(2, 2, 2, 2);

	private Border favoritePadding = new EmptyBorder(0, 6, 0, 6);

	private ListCellRenderer base;

	public LanguageComboBoxCellRenderer(ListCellRenderer base) {
		this.base = base;
		this.padding = new CompoundBorder(padding, ((JLabel) base).getBorder());
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		JLabel c = (JLabel) base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		Language language = (Language) value;
		c.setText(language.getName());
		c.setIcon(ResourceManager.getFlagIcon(language.getCode()));

		// default padding
		c.setBorder(padding);

		LanguageComboBoxModel model = (LanguageComboBoxModel) list.getModel();

		if ((index > 0 && index <= model.favorites().size())) {
			// add favorite padding
			c.setBorder(new CompoundBorder(favoritePadding, c.getBorder()));
		}

		if (index == 0 || index == model.favorites().size()) {
			// add separator border
			c.setBorder(new CompoundBorder(new DashedSeparator(10, 4, Color.lightGray, list.getBackground()), c.getBorder()));
		}

		return c;
	}
}
