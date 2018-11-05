
package net.filebot.util.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.FilteredImageSource;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

public class ListView extends JList {

	protected final BlockSelectionHandler blockSelectionHandler = new BlockSelectionHandler();

	public ListView(ListModel dataModel) {
		super(dataModel);
		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		// better selection behaviour
		putClientProperty("List.isFileList", Boolean.TRUE);
		setDropMode(DropMode.ON);

		setLayoutOrientation(JList.VERTICAL_WRAP);
		setVisibleRowCount(-1);
		setCellRenderer(new ListViewRenderer());

		addMouseListener(blockSelectionHandler);
		addMouseMotionListener(blockSelectionHandler);
	}

	public void addSelectionInterval(Rectangle selection) {
		Point p1 = selection.getLocation();
		Point p2 = new Point(p1.x + selection.width, p1.y + selection.height);

		int startIndex = locationToIndex(p1);
		int endIndex = locationToIndex(p2);

		for (int i = startIndex; i <= endIndex; i++) {
			Rectangle cell = getCellBounds(i, i);

			if (cell != null && selection.intersects(cell)) {
				addSelectionInterval(i, i);
			}
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		Rectangle selection = blockSelectionHandler.getSelection();

		// paint block selection
		if (selection != null) {
			paintBlockSelection((Graphics2D) g, selection);
		}
	}

	protected void paintBlockSelection(Graphics2D g2d, Rectangle selection) {
		g2d.setPaint(SwingUI.derive(getSelectionBackground(), 0.3f));
		g2d.fill(selection);

		g2d.setPaint(getSelectionBackground());
		g2d.draw(selection);
	}

	protected String convertValueToText(Object value) {
		return value.toString();
	}

	protected Icon convertValueToIcon(Object value) {
		return null;
	}

	protected class ListViewRenderer extends DefaultListCellRenderer {

		public ListViewRenderer() {
			setOpaque(false);
		}

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			Icon icon = convertValueToIcon(value);

			if (isSelected && icon != null) {
				// apply selection color tint
				icon = new ImageIcon(createImage(new FilteredImageSource(SwingUI.getImage(icon).getSource(), new ColorTintImageFilter(list.getSelectionBackground(), 0.5f))));
			}

			setText(convertValueToText(value));
			setIcon(icon);

			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			// paint selection background for the text area only, not the whole cell
			int iconWidth = (getIcon() == null ? 0 : getIcon().getIconHeight());
			int startX = iconWidth + getIconTextGap();
			Rectangle2D text = getFontMetrics(getFont()).getStringBounds(getText(), g);

			g.setColor(getBackground());
			g.fillRect(startX - 2, 1, (int) (text.getWidth() + 6), getHeight() - 1);

			super.paintComponent(g);
		}
	};

	protected class BlockSelectionHandler extends MouseInputAdapter {

		private Rectangle selection;

		private Point origin;

		@Override
		public void mousePressed(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e) && !isSelectedIndex(locationToIndex(e.getPoint()))) {
				origin = e.getPoint();
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if (origin == null)
				return;

			// begin selection
			if (selection == null)
				selection = new Rectangle();

			// keep point within component bounds
			Point p2 = e.getPoint();
			p2.x = Math.max(0, Math.min(getWidth() - 1, p2.x));
			p2.y = Math.max(0, Math.min(getHeight() - 1, p2.y));

			// update selection bounds
			selection.setFrameFromDiagonal(origin, p2);

			// auto-scroll
			ensureIndexIsVisible(locationToIndex(p2));

			// update list selection
			clearSelection();
			addSelectionInterval(selection);

			// update view
			repaint();
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			origin = null;

			// end selection
			selection = null;

			// update view
			repaint();
		}

		public Rectangle getSelection() {
			return selection;
		}
	};

}
