package net.filebot.util.ui;

import static net.filebot.Logging.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URI;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

public class LinkButton extends JButton {

	private Color color = getForeground();
	private Color rolloverColor = new Color(0x3399FF);

	public LinkButton(String text, String tooltip, Icon icon, URI uri) {
		this(new OpenUriAction(text, tooltip, icon, uri));
	}

	public LinkButton(Action action) {
		setAction(action);

		setFocusPainted(false);
		setOpaque(false);
		setContentAreaFilled(false);
		setBorder(null);

		setHorizontalAlignment(LEFT);
		setIconTextGap(6);
		setRolloverEnabled(true);

		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	@Override
	public void setRolloverEnabled(boolean enabled) {
		super.setRolloverEnabled(enabled);

		// always remove listener if there is one
		removeMouseListener(rolloverListener);

		if (enabled) {
			// add listener again, if enabled
			addMouseListener(rolloverListener);
		}
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
		this.setForeground(color);
	}

	public Color getRolloverColor() {
		return rolloverColor;
	}

	public void setRolloverColor(Color rolloverColor) {
		this.rolloverColor = rolloverColor;
	}

	protected final MouseListener rolloverListener = new MouseAdapter() {

		@Override
		public void mouseEntered(MouseEvent e) {
			setForeground(rolloverColor);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			setForeground(color);
		}
	};

	protected static class OpenUriAction extends AbstractAction {

		public static final String URI = "uri";

		public OpenUriAction(String text, String tooltip, Icon icon, URI uri) {
			super(text, icon);
			if (uri != null) {
				putValue(URI, uri);
			}
			if (tooltip != null) {
				putValue(SHORT_DESCRIPTION, tooltip);
			}
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			try {
				URI uri = (URI) getValue(URI);

				if (uri != null) {
					Desktop.getDesktop().browse(uri);
				}
			} catch (Exception e) {
				// should not happen
				debug.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

}
