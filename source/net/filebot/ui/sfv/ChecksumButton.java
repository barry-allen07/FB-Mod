
package net.filebot.ui.sfv;

import static java.awt.Color.*;
import static java.awt.Cursor.*;
import static java.awt.Font.*;
import static java.awt.RenderingHints.*;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JToggleButton;

import net.filebot.ResourceManager;

public class ChecksumButton extends JToggleButton {

	private static final Icon contentArea = ResourceManager.getIcon("button.checksum");
	private static final Icon contentAreaSelected = ResourceManager.getIcon("button.checksum.selected");

	public ChecksumButton(Action action) {
		super(action);

		setPreferredSize(new Dimension(Math.max(contentAreaSelected.getIconWidth(), contentArea.getIconWidth()), Math.max(contentAreaSelected.getIconHeight(), contentArea.getIconHeight())));
		setMinimumSize(getPreferredSize());
		setMaximumSize(getPreferredSize());

		setForeground(WHITE);
		setFont(new Font(DIALOG, PLAIN, 11));

		// as image button
		setBorderPainted(false);
		setContentAreaFilled(false);
		setFocusPainted(false);

		setEnabled(true);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);

		// set appropriate cursor
		setCursor(getPredefinedCursor(enabled ? HAND_CURSOR : DEFAULT_CURSOR));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		g2d.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);

		// paint background image in the center
		if (isSelected()) {
			contentAreaSelected.paintIcon(this, g2d, (int) Math.round((getWidth() - contentAreaSelected.getIconWidth()) / (double) 2), (int) Math.round((getHeight() - contentAreaSelected.getIconHeight()) / (double) 2));
		} else {
			contentArea.paintIcon(this, g2d, (int) Math.round((getWidth() - contentArea.getIconWidth()) / (double) 2), (int) Math.round((getHeight() - contentArea.getIconHeight()) / (double) 2));
		}

		Rectangle2D textBounds = g2d.getFontMetrics().getStringBounds(getText(), g2d);

		// draw text in the center
		g2d.drawString(getText(), Math.round((getWidth() - textBounds.getWidth()) / 2) + 1, Math.round(getHeight() / 2 - textBounds.getY() - textBounds.getHeight() / 2));
	}
}
