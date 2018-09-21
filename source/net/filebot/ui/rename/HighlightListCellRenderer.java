package net.filebot.ui.rename;

import static net.filebot.Logging.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import net.filebot.util.ui.AbstractFancyListCellRenderer;
import net.filebot.util.ui.SwingUI;

class HighlightListCellRenderer extends AbstractFancyListCellRenderer {

	protected final JTextComponent textComponent = new JTextField();

	protected final Pattern pattern;
	protected final Highlighter.HighlightPainter highlightPainter;

	public HighlightListCellRenderer(Pattern pattern, Highlighter.HighlightPainter highlightPainter, int padding) {
		super(new Insets(0, 0, 0, 0));

		this.pattern = pattern;
		this.highlightPainter = highlightPainter;

		// pad the cell from inside the text component,
		// so the HighlightPainter may paint in this space as well
		textComponent.setBorder(new EmptyBorder(padding, padding, padding, padding));

		// make text component transparent, should work for all LAFs (setOpaque(false) may not, e.g. Nimbus)
		textComponent.setBackground(SwingUI.TRANSLUCENT);

		this.add(textComponent, BorderLayout.WEST);

		textComponent.getDocument().addDocumentListener(new HighlightUpdateListener());
	}

	@Override
	protected void configureListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		super.configureListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

		textComponent.setText(value.toString());
	}

	protected void updateHighlighter() {
		textComponent.getHighlighter().removeAllHighlights();

		Matcher matcher = pattern.matcher(textComponent.getText());

		while (matcher.find()) {
			try {
				textComponent.getHighlighter().addHighlight(matcher.start(0), matcher.end(0), highlightPainter);
			} catch (BadLocationException e) {
				// should not happen
				debug.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

	@Override
	public void setForeground(Color fg) {
		super.setForeground(fg);

		// textComponent is null while in super constructor
		if (textComponent != null) {
			textComponent.setForeground(fg);
		}
	}

	private class HighlightUpdateListener implements DocumentListener {

		@Override
		public void changedUpdate(DocumentEvent e) {
			updateHighlighter();
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			updateHighlighter();
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			updateHighlighter();
		}

	}

}
