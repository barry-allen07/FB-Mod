package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.channels.Channels;

import org.junit.Test;

public class ByteBufferOutputStreamTest {

	@Test
	public void growBufferAsNeeded() throws Exception {
		// initial buffer size of 1, increase size by a factor of 2 if a bigger buffer is needed
		ByteBufferOutputStream buffer = new ByteBufferOutputStream(1, 1.0f);

		buffer.write("asdf".getBytes("utf-8"));

		// check content
		assertEquals("asdf", UTF_8.decode(buffer.getByteBuffer()).toString());

		// check capacity
		assertEquals(4, buffer.capacity());
	}

	@Test
	public void transferFrom() throws Exception {
		InputStream in = new ByteArrayInputStream("asdf".getBytes("utf-8"));

		ByteBufferOutputStream buffer = new ByteBufferOutputStream(4);

		int n = buffer.transferFrom(Channels.newChannel(in));

		// check number of bytes transfered
		assertEquals(4, n);

		// check content
		assertEquals("asdf", UTF_8.decode(buffer.getByteBuffer()).toString());
	}

}
