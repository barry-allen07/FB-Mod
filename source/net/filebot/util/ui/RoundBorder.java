
package net.filebot.util.ui;


import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.border.AbstractBorder;


public class RoundBorder extends AbstractBorder {

	private final Color color;
	private final Insets insets;
	private final int arc;


	public RoundBorder() {
		this.color = new Color(0xACACAC);
		this.arc = 12;
		this.insets = new Insets(1, 1, 1, 1);
	}


	public RoundBorder(Color color, int arc, Insets insets) {
		this.color = color;
		this.arc = arc;
		this.insets = insets;
	}


	@Override
	public boolean isBorderOpaque() {
		return false;
	}


	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Graphics2D g2d = (Graphics2D) g.create();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2d.setPaint(c.getBackground());
		g2d.fillRoundRect(x, y, width - 1, height - 1, arc, arc);

		g2d.setPaint(color);
		g2d.drawRoundRect(x, y, width - 1, height - 1, arc, arc);

		g2d.dispose();
	}


	@Override
	public Insets getBorderInsets(Component c) {
		return new Insets(insets.top, insets.left, insets.bottom, insets.right);
	}


	@Override
	public Insets getBorderInsets(Component c, Insets insets) {
		insets.top = this.insets.top;
		insets.left = this.insets.left;
		insets.bottom = this.insets.bottom;
		insets.right = this.insets.right;

		return insets;
	}

}
