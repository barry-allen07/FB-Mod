
package net.filebot.ui.rename;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position.Bias;

import net.filebot.util.ui.GradientStyle;

class CharacterHighlightPainter implements Highlighter.HighlightPainter {

	private Color gradientBeginColor;
	private Color gradientEndColor;

	public CharacterHighlightPainter(Color gradientBeginColor, Color gradientEndColor) {
		this.gradientBeginColor = gradientBeginColor;
		this.gradientEndColor = gradientEndColor;
	}

	@Override
	public void paint(Graphics g, int offset1, int offset2, Shape bounds, JTextComponent c) {
		Graphics2D g2d = (Graphics2D) g;

		try {
			// determine locations
			TextUI mapper = c.getUI();
			Rectangle2D p1 = mapper.modelToView2D(c, offset1, Bias.Backward);
			Rectangle2D p2 = mapper.modelToView2D(c, offset2, Bias.Backward);

			Rectangle2D r = p1.createUnion(p2);
			double w = r.getWidth() + 1;
			double h = r.getHeight();
			double x = r.getX() - 1;
			double y = r.getY();
			double arch = 5f;

			RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, arch, arch);

			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setPaint(GradientStyle.TOP_TO_BOTTOM.getGradientPaint(shape, gradientBeginColor, gradientEndColor));

			g2d.fill(shape);
		} catch (BadLocationException e) {
			// should not happen
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, e.toString(), e);
		}
	}
}
