/*
 * Created on 19.03.2005
 */

package net.filebot.util.ui.notification;

public interface NotificationLayout {

	public void add(NotificationWindow notification);

	public void remove(NotificationWindow notification);

	public int size();

}
