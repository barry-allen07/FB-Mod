package net.filebot.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class ByteBufferOutputStream extends OutputStream {

	private ByteBuffer buffer;

	private final float loadFactor;

	public ByteBufferOutputStream(long initialCapacity) {
		this((int) initialCapacity, 1.0f);
	}

	public ByteBufferOutputStream(int initialCapacity) {
		this(initialCapacity, 1.0f);
	}

	public ByteBufferOutputStream(int initialCapacity, float loadFactor) {
		if (initialCapacity < 0)
			throw new IllegalArgumentException("initialCapacity must not be negative: " + initialCapacity);

		if (loadFactor <= 0 || Float.isNaN(loadFactor))
			throw new IllegalArgumentException("loadFactor must be greater than zero: " + loadFactor);

		this.buffer = ByteBuffer.allocate(initialCapacity + 1);
		this.loadFactor = loadFactor;
	}

	@Override
	public void write(int b) throws IOException {
		ensureCapacity(buffer.position() + 1);
		buffer.put((byte) b);
	}

	@Override
	public void write(byte[] src) throws IOException {
		ensureCapacity(buffer.position() + src.length);
		buffer.put(src);
	}

	public void write(ByteBuffer src) throws IOException {
		ensureCapacity(buffer.position() + src.remaining());
		buffer.put(src);
	}

	@Override
	public void write(byte[] src, int offset, int length) throws IOException {
		ensureCapacity(buffer.position() + length);
		buffer.put(src, offset, length);
	}

	public void ensureCapacity(int minCapacity) {
		if (minCapacity <= buffer.capacity())
			return;

		// calculate new buffer size with load factor
		int newCapacity = (int) (buffer.capacity() * (1 + loadFactor));

		// ensure minCapacity
		if (newCapacity < minCapacity)
			newCapacity = minCapacity;

		// create new buffer with increased capacity
		ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);

		// copy current data to new buffer
		buffer.flip();
		newBuffer.put(buffer);

		buffer = newBuffer;
	}

	public ByteBuffer getByteBuffer() {
		ByteBuffer result = buffer.duplicate();

		// flip buffer so it can be read
		result.flip();

		return result;
	}

	public byte[] getByteArray() {
		ByteBuffer data = getByteBuffer();

		// copy data to byte array
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);

		return bytes;
	}

	public int transferFrom(ReadableByteChannel channel) throws IOException {
		// make sure buffer is not at its limit
		ensureCapacity(buffer.position() + 1);

		return channel.read(buffer);
	}

	public int transferFully(InputStream inputStream) throws IOException {
		return transferFully(Channels.newChannel(inputStream));
	}

	public int transferFully(ReadableByteChannel channel) throws IOException {
		int total = 0, read = 0;

		while ((read = transferFrom(channel)) >= 0) {
			total += read;
		}

		return total;
	}

	public int position() {
		return buffer.position();
	}

	public int capacity() {
		return buffer.capacity();
	}

	public void rewind() {
		buffer.rewind();
	}

}
