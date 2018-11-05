
package net.filebot.util.ui;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;
import javax.swing.Timer;


public class ProgressIndicator extends JComponent {

	private float radius = 4.0f;
	private int shapeCount = 3;

	private float strokeWidth = 2f;
	private Stroke stroke = new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

	private Color progressShapeColor = Color.orange;
	private Color backgroundShapeColor = new Color(0f, 0f, 0f, 0.25f);

	private final Rectangle2D frame = new Rectangle2D.Double();
	private final Ellipse2D circle = new Ellipse2D.Double();
	private final Dimension baseSize = new Dimension(32, 32);

	private double alpha = 0;
	private double speed = 24;

	private Timer updateTimer;


	public ProgressIndicator() {
		setPreferredSize(baseSize);

		addComponentListener(new ComponentAdapter() {

			@Override
			public void componentShown(ComponentEvent e) {
				startAnimation();
			}


			@Override
			public void componentHidden(ComponentEvent e) {
				stopAnimation();
			}
		});
	}


	public void animateOnce() {
		if ((alpha += (speed / 1000)) >= 1) {
			alpha -= Math.floor(alpha);
		}
	}


	@Override
	public void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		double a = Math.min(getWidth(), getHeight());

		g2d.scale(a / baseSize.width, a / baseSize.height);

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		frame.setFrame(radius, radius, baseSize.width - radius * 2 - 1, baseSize.height - radius * 2 - 1);

		paintShapes(g2d);
	}


	private void paintShapes(Graphics2D g2d) {
		circle.setFrame(frame);

		g2d.setStroke(stroke);
		g2d.setPaint(backgroundShapeColor);

		g2d.draw(circle);

		Point2D center = new Point2D.Double(frame.getCenterX(), frame.getMinY());

		circle.setFrameFromCenter(center, new Point2D.Double(center.getX() + radius, center.getY() + radius));

		g2d.setStroke(stroke);
		g2d.setPaint(progressShapeColor);

		// base rotation
		g2d.rotate(getTheta(alpha, 1.0), frame.getCenterX(), frame.getCenterY());

		double theta = getTheta(1, shapeCount);

		for (int i = 0; i < shapeCount; i++) {
			g2d.rotate(theta, frame.getCenterX(), frame.getCenterY());
			g2d.fill(circle);
		}
	}


	private double getTheta(double value, double max) {
		return (value / max) * 2 * Math.PI;
	}


	public void startAnimation() {
		if (updateTimer == null) {
			updateTimer = new Timer(20, new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					animateOnce();
					repaint();
				}
			});

			updateTimer.start();
		}
	}


	public void stopAnimation() {
		if (updateTimer != null) {
			updateTimer.stop();
			updateTimer = null;
		}
	}


	public void setShapeCount(int indeterminateShapeCount) {
		this.shapeCount = indeterminateShapeCount;
	}


	public void setSpeed(double speed) {
		this.speed = speed;
	}


	public double getSpeed() {
		return speed;
	}

}
