/*
 * Created on 19.03.2005
 */

package net.filebot.util.ui.notification;

import static net.filebot.util.ui.notification.Direction.*;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QueueNotificationLayout implements NotificationLayout {

	private final List<NotificationWindow> notifications = new ArrayList<NotificationWindow>();

	private final Direction alignment;
	private final Direction direction;
	private final Direction growAnchor;

	public QueueNotificationLayout() {
		this(SOUTH_EAST, WEST);
	}

	public QueueNotificationLayout(Direction alignment, Direction direction) {
		this.alignment = alignment;
		this.growAnchor = alignment;
		this.direction = direction;
	}

	public QueueNotificationLayout(Direction orientation, Direction direction, Direction growAnchor) {
		this.alignment = orientation;
		this.direction = direction;
		this.growAnchor = growAnchor;
	}

	private Point getBaseAnchor(Dimension screen, Insets insets) {
		Point p = new Point();

		screen.height -= insets.top + insets.bottom;
		screen.width -= insets.left + insets.right;

		p.x = (int) (alignment.ax * screen.width);
		p.y = (int) (alignment.ay * screen.height);

		p.x += insets.left;
		p.y += insets.top;

		return p;
	}

	private Point getLocation(Point anchor, Dimension size) {
		Point p = new Point();

		p.x = (int) (anchor.x - size.width * growAnchor.ax);
		p.y = (int) (anchor.y - size.height * growAnchor.ay);

		return p;
	}

	private Point getNextAnchor(Point anchor, Dimension size) {
		Point p = new Point();

		p.x = anchor.x + size.width * direction.vx;
		p.y = anchor.y + size.height * direction.vy;

		return p;
	}

	@Override
	public void add(NotificationWindow notification) {
		notifications.add(notification);
		align(notification.getGraphicsConfiguration());
	}

	private void align(GraphicsConfiguration gc) {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

		// avoid flickering by moving windows in reverse order
		Point anchor = getBaseAnchor(screen, insets);
		align(anchor, screen, notifications.iterator());
	}

	private void align(Point anchor, Dimension screen, Iterator<NotificationWindow> seq) {
		if (!seq.hasNext()) {
			return;
		}

		NotificationWindow window = seq.next();

		Dimension size = window.getSize();
		size.width = Math.min(size.width, (int) (screen.width * 0.8));
		size.height = Math.min(size.height, (int) (screen.height * 0.2));

		Point p = getLocation(anchor, size);
		align(getNextAnchor(anchor, size), screen, seq);

		window.setBounds(p.x, p.y, size.width, size.height);
	}

	@Override
	public void remove(NotificationWindow notification) {
		if (notifications.remove(notification)) {
			align(notification.getGraphicsConfiguration());
		}
	}

	@Override
	public int size() {
		return notifications.size();
	}

}
