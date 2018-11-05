package net.filebot.util.ui;

import static javax.swing.BorderFactory.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultSingleSelectionModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SingleSelectionModel;
import javax.swing.SwingConstants;

public class SelectButton<T> extends JButton {

	public static final String SELECTED_VALUE = "selected value";

	private final Color beginColor = new Color(0xF0EEE4);
	private final Color endColor = new Color(0xE0DED4);

	private final Color beginColorHover = beginColor;
	private final Color endColorHover = new Color(0xD8D7CD);

	private final SelectIcon selectIcon = new SelectIcon();

	private List<T> model = Collections.emptyList();
	private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();

	private LabelProvider<T> labelProvider = new NullLabelProvider<T>();

	private boolean hover = false;

	public SelectButton() {
		setContentAreaFilled(false);
		setFocusable(false);

		super.setIcon(selectIcon);

		setHorizontalAlignment(SwingConstants.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);

		setBorder(createLineBorder(new Color(0xA4A4A4), 1));
		setPreferredSize(new Dimension(32, 22));

		addActionListener(new OpenPopupOnClick());
	}

	public void setModel(Collection<T> model) {
		this.model = new ArrayList<T>(model);
		setSelectedIndex(0);
	}

	public LabelProvider<T> getLabelProvider() {
		return labelProvider;
	}

	public void setLabelProvider(LabelProvider<T> labelProvider) {
		this.labelProvider = labelProvider;

		// update icon
		this.setIcon(labelProvider.getIcon(getSelectedValue()));
	}

	@Override
	public void setIcon(Icon icon) {
		selectIcon.setInnerIcon(icon);
		repaint();
	}

	public void setSelectedValue(T value) {
		setSelectedIndex(model.indexOf(value));
	}

	public T getSelectedValue() {
		if (!selectionModel.isSelected())
			return null;

		return model.get(selectionModel.getSelectedIndex());
	}

	public void setSelectedIndex(int i) {
		if (i < 0 || i >= model.size()) {
			selectionModel.clearSelection();
			setIcon(null);
			return;
		}

		selectionModel.setSelectedIndex(i);
		T value = model.get(i);
		setIcon(labelProvider.getIcon(value));
		firePropertyChange(SELECTED_VALUE, null, value);
	}

	public int getSelectedIndex() {
		return selectionModel.getSelectedIndex();
	}

	public SingleSelectionModel getSelectionModel() {
		return selectionModel;
	}

	public void spinValue(int spin) {
		int size = model.size();

		spin = spin % size;

		int next = getSelectedIndex() + spin;

		if (next < 0)
			next += size;
		else if (next >= size)
			next -= size;

		setSelectedIndex(next);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		Rectangle bounds = new Rectangle(getSize());

		if (hover)
			g2d.setPaint(GradientStyle.TOP_TO_BOTTOM.getGradientPaint(bounds, beginColorHover, endColorHover));
		else
			g2d.setPaint(GradientStyle.TOP_TO_BOTTOM.getGradientPaint(bounds, beginColor, endColor));

		g2d.fill(bounds);

		super.paintComponent(g);
	}

	@Override
	protected void processMouseEvent(MouseEvent e) {
		switch (e.getID()) {
		case MouseEvent.MOUSE_ENTERED:
			hover = true;
			repaint();
			break;
		case MouseEvent.MOUSE_EXITED:
			hover = false;
			repaint();
			break;
		}

		super.processMouseEvent(e);
	}

	private class OpenPopupOnClick implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			JPopupMenu popup = new JPopupMenu();

			for (T value : model) {
				SelectPopupMenuItem item = new SelectPopupMenuItem(labelProvider.getText(value), labelProvider.getIcon(value), value);

				if (value == getSelectedValue())
					item.setSelected(true);

				popup.add(item);
			}

			popup.show(SelectButton.this, 0, getHeight() - 1);
		}
	}

	private class SelectPopupMenuItem extends JMenuItem implements ActionListener {

		private final T value;

		public SelectPopupMenuItem(String text, Icon icon, T value) {
			super(text, icon);

			this.value = value;

			setMargin(new Insets(3, 0, 3, 0));
			addActionListener(this);
		}

		@Override
		public void setSelected(boolean selected) {
			setFont(getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			setSelectedValue(value);
		}

	}

	private static class SelectIcon implements Icon {

		private final GeneralPath arrow;

		private Icon icon;

		public SelectIcon() {
			arrow = new GeneralPath(Path2D.WIND_EVEN_ODD, 3);
			int x = 25;
			int y = 10;

			arrow.moveTo(x - 2, y);
			arrow.lineTo(x, y + 3);
			arrow.lineTo(x + 3, y);
			arrow.lineTo(x - 2, y);
		}

		public void setInnerIcon(Icon icon) {
			this.icon = icon;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = (Graphics2D) g;

			if (icon != null) {
				icon.paintIcon(c, g2d, 4, 3);
			}

			g2d.setPaint(Color.BLACK);
			g2d.fill(arrow);
		}

		@Override
		public int getIconWidth() {
			return 30;
		}

		@Override
		public int getIconHeight() {
			return 20;
		}
	}

}
