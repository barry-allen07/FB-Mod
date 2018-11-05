package net.filebot.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A {@link Console} like class for when {@link System#console()} is not available in the current JVM
 *
 * @see Console
 */
public class PseudoConsole {

	private static PseudoConsole STDIO;

	public static synchronized PseudoConsole getSystemConsole() {
		if (STDIO == null) {
			STDIO = new PseudoConsole(System.in, System.out, StandardCharsets.UTF_8);
		}
		return STDIO;
	}

	private final BufferedReader reader;
	private final PrintWriter writer;

	public PseudoConsole(InputStream in, PrintStream out, Charset cs) {
		reader = new BufferedReader(new InputStreamReader(in, cs));
		writer = new PrintWriter(new OutputStreamWriter(out, cs), true);
	}

	public PrintWriter writer() {
		return writer;
	}

	public Reader reader() {
		return reader;
	}

	public PrintWriter format(String fmt, Object... args) {
		return writer.format(fmt, args);
	}

	public PrintWriter printf(String format, Object... args) {
		return writer.printf(format, args);
	}

	public String readLine(String fmt, Object... args) {
		throw new UnsupportedOperationException();
	}

	public String readLine() throws IOException {
		return reader.readLine();
	}

	public char[] readPassword(String fmt, Object... args) {
		throw new UnsupportedOperationException();
	}

	public char[] readPassword() {
		throw new UnsupportedOperationException();
	}

	public void flush() {
		writer.flush();
	}

}
