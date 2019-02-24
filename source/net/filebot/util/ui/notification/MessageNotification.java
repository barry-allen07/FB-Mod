/*
 * Created on 16.03.2005
 */

package net.filebot.util.ui.notification;

import static javax.swing.BorderFactory.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

public class MessageNotification extends NotificationWindow {

	public MessageNotification(String head, String text, Icon icon, int timeout) {
		this((Window) null, head, text, icon, timeout);
	}

	public MessageNotification(String head, String text, Icon icon) {
		this(head, text, icon, -1);
	}

	private int margin = 10;
	private Border marginBorder = createEmptyBorder(margin, margin, margin, margin);
	private Border border = createCompoundBorder(createEtchedBorder(new Color(245, 155, 15), Color.WHITE), marginBorder);

	private JLabel headLabel;
	private JTextPane textArea;
	private JLabel imageLabel;

	public MessageNotification(Window owner, String head, String text, Icon icon, int timeout) {
		super(owner, timeout);

		JComponent c = (JComponent) getContentPane();

		c.setLayout(new BorderLayout(5, 2));
		c.setBackground(Color.WHITE);
		c.setBorder(border);

		JPanel textPanel = new JPanel(new BorderLayout());
		textPanel.setOpaque(false);
		textPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		headLabel = new JLabel(head);
		headLabel.setHorizontalAlignment(SwingConstants.CENTER);
		headLabel.setFont(headLabel.getFont().deriveFont(Font.BOLD));
		textPanel.add(headLabel, BorderLayout.NORTH);

		textArea = new JTextPane();
		textArea.setText(text);
		textArea.setEditable(false);
		textArea.setOpaque(false);
		textPanel.add(textArea, BorderLayout.CENTER);

		if (icon != null) {
			imageLabel = new JLabel(icon);
			c.add(imageLabel, BorderLayout.WEST);
		}

		c.add(textPanel, BorderLayout.CENTER);
		pack();

		// copy message to clipboard
		getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		getGlassPane().addMouseListener(mouseClicked(evt -> copyToClipboard(text)));
	}

}
