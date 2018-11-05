package net.filebot.cli;

import static javax.swing.BorderFactory.*;
import static net.filebot.Logging.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BorderLayout;
import java.awt.Dialog.ModalExclusionType;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;

import org.fife.ui.rsyntaxtextarea.FileLocation;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.TextEditorPane;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import net.filebot.ApplicationFolder;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.util.TeePrintStream;

public class GroovyPad extends JFrame {

	public static final String DEFAULT_SCRIPT = "runScript 'sysinfo'";

	public GroovyPad() throws IOException {
		super("Groovy Pad");

		RTextScrollPane editorPane = createEditor(Theme.load(Theme.class.getResourceAsStream("themes/eclipse.xml")));
		RTextScrollPane outputPane = createOutputLog(Theme.load(Theme.class.getResourceAsStream("themes/dark.xml")));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, editorPane, outputPane);
		splitPane.setResizeWeight(0.4);

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new BorderLayout(0, 0));
		c.add(splitPane, BorderLayout.CENTER);

		JToolBar tools = new JToolBar("Run", JToolBar.HORIZONTAL);
		tools.setFloatable(true);
		tools.add(run);
		tools.add(cancel);
		tools.addSeparator();
		tools.add(newAction(DEFAULT_SCRIPT, ResourceManager.getIcon("status.info"), evt -> runScript(DEFAULT_SCRIPT)));
		c.add(tools, BorderLayout.NORTH);

		run.setEnabled(true);
		cancel.setEnabled(false);

		installAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), run);
		installAction(c, KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), run);

		addWindowListener(new WindowAdapter() {

			@Override
			public void windowClosed(WindowEvent evt) {
				cancel.actionPerformed(null);
				console.unhook();

				try {
					editor.save();
				} catch (IOException e) {
					// ignore
				}
			}
		});

		console = new MessageConsole(output);
		console.hook();

		shell = createScriptShell();
		editor.requestFocusInWindow();

		setModalExclusionType(ModalExclusionType.TOOLKIT_EXCLUDE);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setLocationByPlatform(true);
		setSize(800, 600);
	}

	protected MessageConsole console;
	protected TextEditorPane editor;
	protected TextEditorPane output;

	protected RTextScrollPane createEditor(Theme theme) {
		editor = new TextEditorPane(TextEditorPane.INSERT_MODE, false);
		theme.apply(editor);

		editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY);
		editor.setAutoscrolls(false);
		editor.setAnimateBracketMatching(false);
		editor.setAntiAliasingEnabled(true);
		editor.setAutoIndentEnabled(true);
		editor.setBracketMatchingEnabled(true);
		editor.setCloseCurlyBraces(true);
		editor.setClearWhitespaceLinesEnabled(true);
		editor.setCodeFoldingEnabled(true);
		editor.setHighlightSecondaryLanguages(false);
		editor.setRoundedSelectionEdges(false);
		editor.setTabsEmulated(false);

		try {
			// use this default value so people can easily submit bug reports with fn:sysinfo logs
			File pad = ApplicationFolder.AppData.resolve("pad.groovy");

			if (!pad.exists()) {
				ScriptShellMethods.saveAs(DEFAULT_SCRIPT, pad);
			}

			// restore on open
			editor.load(FileLocation.create(pad), "UTF-8");
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		return new RTextScrollPane(editor, true);
	}

	protected RTextScrollPane createOutputLog(Theme theme) throws IOException {
		output = new TextEditorPane(TextEditorPane.INSERT_MODE, false);
		theme.apply(output);

		output.setEditable(false);
		output.setReadOnly(true);
		output.setAutoscrolls(true);

		output.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HOSTS);
		output.setAnimateBracketMatching(false);
		output.setAntiAliasingEnabled(true);
		output.setAutoIndentEnabled(false);
		output.setBracketMatchingEnabled(false);
		output.setCloseCurlyBraces(false);
		output.setClearWhitespaceLinesEnabled(false);
		output.setCodeFoldingEnabled(false);
		output.setHighlightCurrentLine(false);
		output.setHighlightSecondaryLanguages(false);
		output.setRoundedSelectionEdges(false);
		output.setTabsEmulated(false);

		output.setBorder(createEmptyBorder(2, 2, 2, 2));

		RTextScrollPane sp = new RTextScrollPane(output, true);
		sp.setBorder(createEmptyBorder());
		sp.setLineNumbersEnabled(false);
		return sp;
	}

	protected final ScriptShell shell;

	protected ScriptShell createScriptShell() {
		try {
			return new ScriptShell(s -> ScriptSource.GITHUB_STABLE.getScriptProvider(s).getScript(s), new CmdlineOperations(), new HashMap<String, Object>());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected final Action run = newAction("Run", ResourceManager.getIcon("script.go"), this::runScript);
	protected final Action cancel = newAction("Cancel", ResourceManager.getIcon("script.cancel"), this::cancelScript);

	private Runner currentRunner = null;

	public void runScript(ActionEvent evt) {
		try {
			editor.save();
		} catch (IOException e) {
			debug.log(Level.WARNING, e, e::toString);
		}

		runScript(editor.getText());
	}

	public void runScript(String script) {
		if (currentRunner == null || currentRunner.isDone()) {
			currentRunner = new Runner(script) {

				@Override
				protected void done() {
					run.setEnabled(true);
					cancel.setEnabled(false);
				}
			};

			run.setEnabled(false);
			cancel.setEnabled(true);
			output.setText(null);

			currentRunner.execute();
		}
	}

	@SuppressWarnings("deprecation")
	protected void cancelScript(ActionEvent evt) {
		if (currentRunner != null && !currentRunner.isDone()) {
			currentRunner.cancel(true);
			currentRunner.getExecutionThread().stop();

			try {
				currentRunner.get(2, TimeUnit.SECONDS);
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::getMessage);
			}
		}
	}

	protected class Runner extends SwingWorker<Object, Object> {

		private Thread executionThread;
		private Object result;

		public Runner(final String script) {
			executionThread = new Thread("GroovyPadRunner") {

				@Override
				public void run() {
					try {
						Bindings bindings = new SimpleBindings();
						bindings.put(ScriptShell.SHELL_ARGS_BINDING_NAME, Settings.getApplicationArguments());
						bindings.put(ScriptShell.ARGV_BINDING_NAME, Settings.getApplicationArguments().getFiles(false));

						result = shell.evaluate(script, bindings);

						// print result and make sure to flush Groovy output
						SimpleBindings resultBindings = new SimpleBindings();
						resultBindings.put("result", result);
						if (result != null) {
							shell.evaluate("print('Result: '); println(result);", resultBindings);
						} else {
							shell.evaluate("println();", resultBindings);
						}
					} catch (ScriptException e) {
						while (e.getCause() instanceof ScriptException) {
							e = (ScriptException) e.getCause();
						}
						e.printStackTrace();
					} catch (Throwable e) {
						e.printStackTrace();
					}
				};
			};

			executionThread.setDaemon(false);
			executionThread.setPriority(Thread.MIN_PRIORITY);
		}

		@Override
		protected Object doInBackground() throws Exception {
			executionThread.start();
			executionThread.join();
			return result;
		}

		public Thread getExecutionThread() {
			return executionThread;
		}
	};

	public static class MessageConsole {

		private final PrintStream system_out = System.out;
		private final PrintStream system_err = System.err;

		private JTextComponent textComponent;

		public MessageConsole(JTextComponent textComponent) {
			this.textComponent = textComponent;
		}

		public void hook() {
			try {
				System.setOut(new TeePrintStream(new ConsoleOutputStream(), true, "UTF-8", system_out));
				System.setErr(new TeePrintStream(new ConsoleOutputStream(), true, "UTF-8", system_err));
			} catch (UnsupportedEncodingException e) {
				debug.log(Level.WARNING, e, e::getMessage);
			}
		}

		public void unhook() {
			System.setOut(system_out);
			System.setErr(system_err);
		}

		private class ConsoleOutputStream extends ByteArrayOutputStream {

			@Override
			public void flush() {
				try {
					String message = this.toString("UTF-8");
					reset();
					commit(message);
				} catch (Exception e) {
					// can't happen
				}
			}

			private void commit(final String line) {
				SwingUtilities.invokeLater(() -> {
					try {
						int offset = textComponent.getDocument().getLength();
						textComponent.getDocument().insertString(offset, line, null);
						textComponent.setCaretPosition(textComponent.getDocument().getLength());
					} catch (Exception e) {
						// ignore
					}
				});
			}
		}
	}

}
