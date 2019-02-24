
package net.filebot.util.ui;


import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;


public class FancyTreeCellRenderer extends DefaultTreeCellRenderer {

	private Color gradientBeginColor;
	private Color gradientEndColor;
	private GradientStyle gradientStyle;
	private boolean paintGradient;

	private Color backgroundSelectionColor;


	public FancyTreeCellRenderer() {
		this(GradientStyle.TOP_TO_BOTTOM);
	}


	public FancyTreeCellRenderer(GradientStyle gradientStyle) {
		this.gradientStyle = gradientStyle;

		backgroundSelectionColor = getBackgroundSelectionColor();

		// disable default selection background
		setBackgroundSelectionColor(null);
	}


	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
		super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);

		setIconTextGap(5);

		if (selected) {
			setPaintGradient(true);
			setGradientBeginColor(backgroundSelectionColor.brighter());
			setGradientEndColor(backgroundSelectionColor);
		} else {
			setPaintGradient(false);
		}

		return this;
	}


	@Override
	protected void paintComponent(Graphics g) {
		if (isPaintGradient()) {
			Graphics2D g2d = (Graphics2D) g;

			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int imageOffset = getLabelStart() - 2;

			int arch = 16;
			RoundRectangle2D shape = new RoundRectangle2D.Double(imageOffset, 1, getWidth() - imageOffset, getHeight() - 2, arch, arch);

			g2d.setPaint(gradientStyle.getGradientPaint(shape, gradientBeginColor, gradientEndColor));
			g2d.fill(shape);
		}

		super.paintComponent(g);
	}


	protected int getLabelStart() {
		Icon icon = getIcon();

		if ((icon != null) && (getText() != null)) {
			return icon.getIconWidth() + Math.max(0, getIconTextGap() - 1);
		}

		return 0;
	}


	public Color getGradientBeginColor() {
		return gradientBeginColor;
	}


	public void setGradientBeginColor(Color gradientBeginColor) {
		this.gradientBeginColor = gradientBeginColor;
	}


	public boolean isPaintGradient() {
		return paintGradient;
	}


	public void setPaintGradient(boolean gradientEnabled) {
		this.paintGradient = gradientEnabled;
	}


	public Color getGradientEndColor() {
		return gradientEndColor;
	}


	public void setGradientEndColor(Color gradientEndColor) {
		this.gradientEndColor = gradientEndColor;
	}


	public GradientStyle getGradientStyle() {
		return gradientStyle;
	}


	public void setGradientStyle(GradientStyle gradientStyle) {
		this.gradientStyle = gradientStyle;
	}

}
