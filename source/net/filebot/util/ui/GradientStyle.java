
package net.filebot.util.ui;


import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.geom.Point2D;
import java.awt.geom.RectangularShape;


public enum GradientStyle {
	TOP_TO_BOTTOM,
	BOTTOM_TO_TOP,
	LEFT_TO_RIGHT,
	RIGHT_TO_LEFT,
	TOP_LEFT_TO_BOTTOM_RIGHT,
	BOTTOM_RIGHT_TO_TOP_LEFT,
	TOP_RIGHT_TO_BOTTOM_LEFT,
	BOTTOM_LEFT_TO_TOP_RIGHT;

	public LinearGradientPaint getGradientPaint(RectangularShape shape, Color gradientBeginColor, Color gradientEndColor) {
		Point2D start = null;
		Point2D end = null;

		switch (this) {
		case BOTTOM_TO_TOP:
			start = new Point2D.Double(shape.getCenterX(), shape.getMaxY());
			end = new Point2D.Double(shape.getCenterX(), shape.getMinY());
			break;

		case TOP_TO_BOTTOM:
			end = new Point2D.Double(shape.getCenterX(), shape.getMaxY());
			start = new Point2D.Double(shape.getCenterX(), shape.getMinY());
			break;

		case LEFT_TO_RIGHT:
			start = new Point2D.Double(shape.getMinX(), shape.getCenterY());
			end = new Point2D.Double(shape.getMaxX(), shape.getCenterY());
			break;

		case RIGHT_TO_LEFT:
			end = new Point2D.Double(shape.getMinX(), shape.getCenterY());
			start = new Point2D.Double(shape.getMaxX(), shape.getCenterY());
			break;

		case TOP_LEFT_TO_BOTTOM_RIGHT:
			start = new Point2D.Double(shape.getMinX(), shape.getMinY());
			end = new Point2D.Double(shape.getMaxX(), shape.getMaxY());
			break;

		case BOTTOM_RIGHT_TO_TOP_LEFT:
			end = new Point2D.Double(shape.getMinX(), shape.getMinY());
			start = new Point2D.Double(shape.getMaxX(), shape.getMaxY());
			break;

		case TOP_RIGHT_TO_BOTTOM_LEFT:
			start = new Point2D.Double(shape.getMaxX(), shape.getMinY());
			end = new Point2D.Double(shape.getMinX(), shape.getMaxY());
			break;

		case BOTTOM_LEFT_TO_TOP_RIGHT:
			end = new Point2D.Double(shape.getMaxX(), shape.getMinY());
			start = new Point2D.Double(shape.getMinX(), shape.getMaxY());
			break;

		default:
			return null;
		}

		Color[] colors = { gradientBeginColor, gradientEndColor };
		float[] fractions = { 0.0f, 1.0f };
		return new LinearGradientPaint(start, end, fractions, colors, CycleMethod.NO_CYCLE);
	}

}
