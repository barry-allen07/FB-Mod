
package net.filebot.util;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


public class ByteBufferInputStream extends InputStream {

	private final ByteBuffer buffer;


	public ByteBufferInputStream(ByteBuffer buffer) {
		this.buffer = buffer;
	}


	@Override
	public int read() throws IOException {
		return (buffer.position() < buffer.limit()) ? (buffer.get() & 0xff) : -1;
	}


	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		}

		if (buffer.position() >= buffer.limit()) {
			return -1;
		}

		if (len > buffer.remaining()) {
			len = buffer.remaining();
		}

		if (len <= 0) {
			return 0;
		}

		buffer.get(b, off, len);
		return len;
	}


	@Override
	public int available() throws IOException {
		return buffer.remaining();
	}


	@Override
	public boolean markSupported() {
		return true;
	}


	@Override
	public void mark(int readlimit) {
		buffer.mark();
	}


	@Override
	public void reset() throws IOException {
		buffer.reset();
	}

}
