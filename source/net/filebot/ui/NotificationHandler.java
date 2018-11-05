package net.filebot.ui;

import static net.filebot.util.ui.notification.Direction.*;

import java.awt.GraphicsEnvironment;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.swing.Icon;
import javax.swing.SwingUtilities;

import net.filebot.ResourceManager;
import net.filebot.util.ui.notification.MessageNotification;
import net.filebot.util.ui.notification.NotificationManager;
import net.filebot.util.ui.notification.QueueNotificationLayout;

public class NotificationHandler extends Handler {

	private final String title;
	private final int timeout;
	private final NotificationManager manager;

	public NotificationHandler(String title) {
		this(title, 2500, new NotificationManager(new QueueNotificationLayout(NORTH, SOUTH), 5));
	}

	public NotificationHandler(String title, int timeout, NotificationManager manager) {
		this.title = title;
		this.timeout = timeout;
		this.manager = manager;

		setFormatter(new SimpleMessageFormatter());
		setLevel(Level.INFO);
	}

	@Override
	public void publish(LogRecord record) {
		// fail gracefully on an headless machine
		if (GraphicsEnvironment.isHeadless())
			return;

		String message = getFormatter().format(record);
		Level level = record.getLevel();

		SwingUtilities.invokeLater(() -> {
			if (level == Level.INFO) {
				show(message, ResourceManager.getIcon("message.info"), timeout * 1);
			} else if (level == Level.WARNING) {
				show(message, ResourceManager.getIcon("message.warning"), timeout * 2);
			} else if (level == Level.SEVERE) {
				show(message, ResourceManager.getIcon("message.error"), timeout * 3);
			}
		});
	}

	protected void show(String message, Icon icon, int timeout) {
		manager.show(new MessageNotification(title, message, icon, timeout));
	}

	@Override
	public void close() throws SecurityException {

	}

	@Override
	public void flush() {

	}

	public static class SimpleMessageFormatter extends Formatter {

		@Override
		public String format(LogRecord record) {
			String message = record.getMessage();
			if (message == null && record.getThrown() != null) {
				message = record.getThrown().toString();
			}
			return message;
		}
	}

}
