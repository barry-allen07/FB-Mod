/*
 * Created on 19.03.2005
 */

package net.filebot.util.ui.notification;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import net.filebot.util.ui.SwingUI;

public class NotificationManager {

	private final NotificationLayout layout;
	private final int limit;

	public NotificationManager() {
		this(new QueueNotificationLayout(), 5);
	}

	public NotificationManager(NotificationLayout layout, int limit) {
		this.layout = layout;
		this.limit = limit;
	}

	public void show(NotificationWindow notification) {
		SwingUI.checkEventDispatchThread();

		if (layout.size() < limit) {
			layout.add(notification);
			notification.addWindowListener(new RemoveListener());
			notification.setVisible(true);
		}
	}

	private class RemoveListener extends WindowAdapter {

		@Override
		public void windowClosing(WindowEvent e) {
			layout.remove((NotificationWindow) e.getWindow());
		}
	}

}
