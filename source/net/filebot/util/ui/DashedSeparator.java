
package net.filebot.util.ui;


import static java.awt.BasicStroke.*;
import static java.awt.RenderingHints.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.border.Border;


public class DashedSeparator implements Border {

	private final int height;
	private final int dash;

	private final Color foreground;
	private final Color background;


	public DashedSeparator(int height, int dash, Color foreground, Color background) {
		this.height = height;
		this.dash = dash;
		this.foreground = foreground;
		this.background = background;
	}


	@Override
	public Insets getBorderInsets(Component c) {
		return new Insets(0, 0, height, 0);
	}


	@Override
	public boolean isBorderOpaque() {
		return true;
	}


	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
		Graphics2D g2d = (Graphics2D) g.create(x, h - this.height, w, h);

		// fill background
		g2d.setPaint(background);
		g2d.fillRect(0, 0, w, h);

		// draw dashed line
		g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
		g2d.setColor(foreground);
		g2d.setStroke(new BasicStroke(1, CAP_ROUND, JOIN_ROUND, 1, new float[] { dash }, 0));

		g2d.drawLine(dash, this.height / 2, w - dash, this.height / 2);

		g2d.dispose();
	}
}
