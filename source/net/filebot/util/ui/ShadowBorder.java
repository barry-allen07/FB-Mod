
package net.filebot.util.ui;


import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;

import javax.swing.border.AbstractBorder;


public class ShadowBorder extends AbstractBorder {

	private int smoothness;

	private int smoothnessOffset;

	private int offset;


	public ShadowBorder() {
		this(2, 2, 12);
	}


	public ShadowBorder(int offset, int smoothness, int smoothnessOffset) {
		this.offset = offset;
		this.smoothness = smoothness;
		this.smoothnessOffset = smoothnessOffset;
	}


	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Graphics2D g2d = (Graphics2D) g;

		Color bg = new Color(0, 0, 0, 81);
		Color faded = new Color(0, 0, 0, 0);

		int a = smoothness + smoothnessOffset;

		Rectangle2D main = new Rectangle2D.Double(a, a, width - a * 2, height - a * 2);

		g2d.setPaint(bg);
		g2d.fill(main);

		Rectangle2D right = new Rectangle2D.Double(main.getMaxX(), a, a, main.getHeight());
		Rectangle2D left = new Rectangle2D.Double(0, a, a, main.getHeight());
		Rectangle2D top = new Rectangle2D.Double(a, 0, main.getWidth(), a);
		Rectangle2D bottom = new Rectangle2D.Double(a, main.getMaxY(), main.getWidth(), a);

		g2d.setPaint(GradientStyle.LEFT_TO_RIGHT.getGradientPaint(right, bg, faded));
		g2d.fill(right);

		g2d.setPaint(GradientStyle.RIGHT_TO_LEFT.getGradientPaint(left, bg, faded));
		g2d.fill(left);

		g2d.setPaint(GradientStyle.BOTTOM_TO_TOP.getGradientPaint(top, bg, faded));
		g2d.fill(top);

		g2d.setPaint(GradientStyle.TOP_TO_BOTTOM.getGradientPaint(bottom, bg, faded));
		g2d.fill(bottom);

		Rectangle2D topLeftCorner = new Rectangle2D.Double(0, 0, a, a);
		Rectangle2D topRightCorner = new Rectangle2D.Double(width - a, 0, a, a);
		Rectangle2D bottomLeftCorner = new Rectangle2D.Double(0, height - a, a, a);
		Rectangle2D bottomRightCorner = new Rectangle2D.Double(width - a, height - a, a, a);

		g2d.setPaint(CornerGradientStyle.TOP_LEFT.getGradientPaint(topLeftCorner, a, bg, faded));
		g2d.fill(topLeftCorner);

		g2d.setPaint(CornerGradientStyle.TOP_RIGHT.getGradientPaint(topRightCorner, a, bg, faded));
		g2d.fill(topRightCorner);

		g2d.setPaint(CornerGradientStyle.BOTTOM_LEFT.getGradientPaint(bottomLeftCorner, a, bg, faded));
		g2d.fill(bottomLeftCorner);

		g2d.setPaint(CornerGradientStyle.BOTTOM_RIGHT.getGradientPaint(bottomRightCorner, a, bg, faded));
		g2d.fill(bottomRightCorner);
	}


	@Override
	public Insets getBorderInsets(Component c) {
		return getBorderInsets(c, new Insets(0, 0, 0, 0));
	}


	@Override
	public Insets getBorderInsets(Component c, Insets insets) {
		insets.top = insets.left = Math.max(smoothness - offset, 4);
		insets.bottom = insets.right = Math.max(smoothness + offset, 4);
		return insets;
	}


	private static enum CornerGradientStyle {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT;

		public RadialGradientPaint getGradientPaint(RectangularShape shape, float radius, Color gradientBeginColor, Color gradientEndColor) {
			Point2D center = null;

			switch (this) {
			case TOP_LEFT:
				center = new Point2D.Double(shape.getX() + radius, shape.getY() + radius);
				break;

			case TOP_RIGHT:
				center = new Point2D.Double(shape.getX() + 0, shape.getY() + radius);
				break;

			case BOTTOM_LEFT:
				center = new Point2D.Double(shape.getX() + radius, shape.getY() + 0);
				break;

			case BOTTOM_RIGHT:
				center = new Point2D.Double(shape.getX() + 0, shape.getY() + 0);
				break;

			default:
				return null;
			}

			float[] dist = { 0.0f, 1.0f };
			Color[] colors = { gradientBeginColor, gradientEndColor };

			return new RadialGradientPaint(center, radius, dist, colors);
		}
	}

}
