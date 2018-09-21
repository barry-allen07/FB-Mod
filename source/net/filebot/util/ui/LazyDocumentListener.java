
package net.filebot.util.ui;

import java.util.function.Consumer;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class LazyDocumentListener implements DocumentListener {

	private Timer timer;

	private DocumentEvent lastEvent = null;

	public LazyDocumentListener(Consumer<DocumentEvent> handler) {
		this(200, handler);
	}

	public LazyDocumentListener(int delay, Consumer<DocumentEvent> handler) {
		timer = new Timer(delay, evt -> {
			handler.accept(lastEvent);
			lastEvent = null;
		});
		timer.setRepeats(false);
	}

	@Override
	public void changedUpdate(DocumentEvent evt) {
		lastEvent = evt;
		timer.restart();
	}

	@Override
	public void insertUpdate(DocumentEvent evt) {
		lastEvent = evt;
		timer.restart();
	}

	@Override
	public void removeUpdate(DocumentEvent evt) {
		lastEvent = evt;
		timer.restart();
	}

}
