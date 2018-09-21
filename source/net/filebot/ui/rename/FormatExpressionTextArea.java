package net.filebot.ui.rename;

import static java.awt.Font.*;
import static net.filebot.Logging.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Font;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.event.DocumentEvent;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

import net.filebot.util.ui.LazyDocumentListener;

public class FormatExpressionTextArea extends RSyntaxTextArea {

	public FormatExpressionTextArea() {
		this(new RSyntaxDocument(new FormatExpressionTokenMakerFactory(), FormatExpressionTokenMakerFactory.SYNTAX_STYLE_GROOVY_FORMAT_EXPRESSION));
	}

	public FormatExpressionTextArea(RSyntaxDocument syntaxDocument) {
		super(syntaxDocument, "", 1, 80);

		try {
			Theme.load(FormatExpressionTextArea.class.getResourceAsStream("FormatExpressionTextArea.Theme.xml")).apply(this);
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		setAntiAliasingEnabled(true);
		setAnimateBracketMatching(true);
		setAutoIndentEnabled(true);
		setBracketMatchingEnabled(true);
		setCloseCurlyBraces(true);
		setCodeFoldingEnabled(false);
		setHyperlinksEnabled(false);
		setUseFocusableTips(false);
		setClearWhitespaceLinesEnabled(false);
		setHighlightCurrentLine(false);
		setHighlightSecondaryLanguages(false);
		setLineWrap(false);
		setMarkOccurrences(false);
		setPaintMarkOccurrencesBorder(false);
		setPaintTabLines(false);
		setFont(new Font(MONOSPACED, PLAIN, 14));

		// dynamically resize the code editor depending on how many lines the current format expression has
		getDocument().addDocumentListener(new LazyDocumentListener(0, evt -> {
			int r1 = getRows();
			int r2 = (int) getText().chars().filter(c -> c == '\n').count() + 1;
			if (r1 != r2) {
				setRows(r2);
				getWindow(FormatExpressionTextArea.this).revalidate();
			}
		}));
	}

	public void onChange(Consumer<DocumentEvent> handler) {
		getDocument().addDocumentListener(new LazyDocumentListener(handler));
	}

	public void onChange(int delay, Consumer<DocumentEvent> handler) {
		getDocument().addDocumentListener(new LazyDocumentListener(delay, handler));
	}

}
