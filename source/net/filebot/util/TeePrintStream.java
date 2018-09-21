
package net.filebot.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class TeePrintStream extends PrintStream {

	private final PrintStream cc;

	public TeePrintStream(OutputStream out, boolean autoFlush, String encoding, PrintStream cc) throws UnsupportedEncodingException {
		super(out, autoFlush, encoding);
		this.cc = cc;
	}

	@Override
	public void close() {
		super.close();
		cc.close();
	}

	@Override
	public void flush() {
		super.flush();
		cc.flush();
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		super.write(buf, off, len);
		cc.write(buf, off, len);
	}

	@Override
	public void write(int b) {
		super.write(b);
		cc.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		super.write(b);
		cc.write(b);
	}

}
