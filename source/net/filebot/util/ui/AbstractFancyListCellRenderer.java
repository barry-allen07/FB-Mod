
package net.filebot.util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public abstract class AbstractFancyListCellRenderer extends JPanel implements ListCellRenderer {

	private Color gradientBeginColor;
	private Color gradientEndColor;

	private Color highlightColor;

	private boolean borderPainted = false;
	private boolean gradientPainted = false;

	private GradientStyle gradientStyle = GradientStyle.TOP_TO_BOTTOM;
	private boolean highlightingEnabled = true;

	private final Insets margin;

	private static final Insets DEFAULT_PADDING = new Insets(7, 7, 7, 7);
	private static final Insets DEFAULT_MARGIN = new Insets(1, 1, 0, 1);

	public AbstractFancyListCellRenderer() {
		this(DEFAULT_PADDING, DEFAULT_MARGIN, null);
	}

	public AbstractFancyListCellRenderer(Insets padding) {
		this(padding, DEFAULT_MARGIN, null);
	}

	public AbstractFancyListCellRenderer(Insets padding, Insets margin) {
		this(padding, margin, null);
	}

	public AbstractFancyListCellRenderer(Insets padding, Insets margin, Color borderColor) {
		this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

		Border border = null;

		if (padding != null)
			border = new EmptyBorder(padding);

		if (borderColor != null)
			border = new CompoundBorder(new LineBorder(borderColor, 1), border);

		if (margin != null) {
			this.margin = margin;
			border = new CompoundBorder(new EmptyBorder(margin), border);
		} else {
			this.margin = new Insets(0, 0, 0, 0);
		}

		setBorder(border);
		setOpaque(false);
	}

	@Override
	protected void paintBorder(Graphics g) {
		if (borderPainted) {
			super.paintBorder(g);
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		Rectangle2D shape = new Rectangle2D.Double(margin.left, margin.top, getWidth() - (margin.left + margin.right), getHeight() - (margin.top + margin.bottom));

		if (isOpaque()) {
			g2d.setPaint(getBackground());
			g2d.fill(shape);
		}

		if (highlightingEnabled && (highlightColor != null)) {
			g2d.setPaint(highlightColor);
			g2d.fill(shape);
		}

		if (gradientPainted) {
			g2d.setPaint(gradientStyle.getGradientPaint(shape, gradientBeginColor, gradientEndColor));
			g2d.fill(shape);
		}

		super.paintComponent(g);
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		configureListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		validate();

		return this;
	}

	protected void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		setGradientPainted(isSelected);
		setBorderPainted(isSelected);

		Color sc = list.getSelectionBackground();

		if (isSelected) {
			setGradientColors(sc.brighter(), sc);
		}

		if (highlightingEnabled && ((index % 2) == 0)) {
			setHighlightColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 28));
		} else {
			setHighlightColor(null);
		}

		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
		}
	}

	public void setGradientColors(Color gradientBeginColor, Color gradientEndColor) {
		this.gradientBeginColor = gradientBeginColor;
		this.gradientEndColor = gradientEndColor;
	}

	public Color getGradientBeginColor() {
		return gradientBeginColor;
	}

	public Color getGradientEndColor() {
		return gradientEndColor;
	}

	public void setHighlightColor(Color highlightColor) {
		this.highlightColor = highlightColor;
	}

	public void setGradientStyle(GradientStyle gradientStyle) {
		this.gradientStyle = gradientStyle;
	}

	public void setHighlightingEnabled(boolean highlightingEnabled) {
		this.highlightingEnabled = highlightingEnabled;
	}

	public void setBorderPainted(boolean borderPainted) {
		this.borderPainted = borderPainted;
	}

	public void setGradientPainted(boolean gradientPainted) {
		this.gradientPainted = gradientPainted;
	}

	public Color getHighlightColor() {
		return highlightColor;
	}

	public boolean isBorderPainted() {
		return borderPainted;
	}

	public GradientStyle getGradientStyle() {
		return gradientStyle;
	}

	public boolean isHighlightingEnabled() {
		return highlightingEnabled;
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint() {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(long tm, int x, int y, int width, int height) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void repaint(Rectangle r) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, char oldValue, char newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, short oldValue, short newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, int oldValue, int newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, long oldValue, long newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, float oldValue, float newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, double oldValue, double newValue) {
	}

	/**
	 * Overridden for performance reasons.
	 */
	@Override
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
	}

}
