
package net.filebot.util.ui.notification;


import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;

import javax.swing.border.AbstractBorder;

import net.filebot.util.ui.GradientStyle;


public class SeparatorBorder extends AbstractBorder {

	private int borderWidth;

	private Color beginColor;

	private Color endColor;

	private GradientStyle gradientStyle;

	private Position position;


	public static enum Position {
		TOP,
		BOTTOM,
		LEFT,
		RIGHT;

		public Rectangle2D getRectangle(RectangularShape shape, int borderWidth) {
			switch (this) {
			case TOP:
				return new Rectangle2D.Double(shape.getX(), shape.getY(), shape.getWidth(), borderWidth);
			case BOTTOM:
				return new Rectangle2D.Double(shape.getX(), shape.getMaxY() - borderWidth, shape.getWidth(), borderWidth);
			case LEFT:
				return new Rectangle2D.Double(shape.getX(), shape.getY(), borderWidth, shape.getHeight());
			case RIGHT:
				return new Rectangle2D.Double(shape.getMaxX() - borderWidth, shape.getY(), borderWidth, shape.getHeight());
			default:
				return null;
			}
		}


		public Insets getInsets(Insets insets, int borderWidth) {
			switch (this) {
			case TOP:
				insets.top = borderWidth;
				insets.left = insets.right = insets.bottom = 0;
				return insets;
			case BOTTOM:
				insets.bottom = borderWidth;
				insets.left = insets.right = insets.top = 0;
				return insets;
			case LEFT:
				insets.left = borderWidth;
				insets.right = insets.top = insets.bottom = 0;
				return insets;
			case RIGHT:
				insets.right = borderWidth;
				insets.left = insets.top = insets.bottom = 0;
				return insets;
			default:
				return null;
			}
		}
	}


	public SeparatorBorder(int height, Color color, Position position) {
		this(height, color, null, null, position);
	}


	public SeparatorBorder(int height, Color color, GradientStyle gradientStyle, Position position) {
		this(height, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0), gradientStyle, position);
	}


	public SeparatorBorder(int height, Color beginColor, Color endColor, GradientStyle gradientStyle, Position position) {
		this.borderWidth = height;
		this.beginColor = beginColor;
		this.endColor = endColor;
		this.gradientStyle = gradientStyle;
		this.position = position;
	}


	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Graphics2D g2d = (Graphics2D) g;

		Rectangle2D shape = position.getRectangle(new Rectangle2D.Double(x, y, width, height), this.borderWidth);

		if (gradientStyle != null && endColor != null)
			g2d.setPaint(gradientStyle.getGradientPaint(shape, beginColor, endColor));
		else
			g2d.setPaint(beginColor);

		g2d.fill(shape);
	}


	@Override
	public Insets getBorderInsets(Component c) {
		return getBorderInsets(c, new Insets(0, 0, 0, 0));
	}


	@Override
	public Insets getBorderInsets(Component c, Insets insets) {
		return position.getInsets(insets, borderWidth);
	}


	public Color getBeginColor() {
		return beginColor;
	}


	public void setBeginColor(Color beginColor) {
		this.beginColor = beginColor;
	}


	public Color getEndColor() {
		return endColor;
	}


	public void setEndColor(Color endColor) {
		this.endColor = endColor;
	}


	public GradientStyle getGradientStyle() {
		return gradientStyle;
	}


	public void setGradientStyle(GradientStyle gradientStyle) {
		this.gradientStyle = gradientStyle;
	}


	public int getBorderWidth() {
		return borderWidth;
	}


	public void setBorderWidth(int height) {
		this.borderWidth = height;
	}


	public Position getPosition() {
		return position;
	}


	public void setPosition(Position position) {
		this.position = position;
	}

}
