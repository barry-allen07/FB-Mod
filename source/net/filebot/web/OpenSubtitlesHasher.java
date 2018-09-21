package net.filebot.web;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;

/**
 * Hash code is based on Media Player Classic. In natural language it calculates: size + 64bit checksum of the first and last 64k (even if they overlap because the file is smaller than 128k).
 */
public final class OpenSubtitlesHasher {

	/**
	 * Size of the chunks that will be hashed in bytes (64 KB)
	 */
	public static final int HASH_CHUNK_SIZE = 64 * 1024;

	public static String computeHashNIO(File file) throws IOException {
		long size = file.length();
		long chunkSizeForFile = Math.min(HASH_CHUNK_SIZE, size);

		try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
			long head = computeHashForChunk(channel.map(MapMode.READ_ONLY, 0, chunkSizeForFile));
			long tail = computeHashForChunk(channel.map(MapMode.READ_ONLY, Math.max(size - HASH_CHUNK_SIZE, 0), chunkSizeForFile));

			return String.format("%016x", size + head + tail);
		}
	}

	public static String computeHash(File file) throws IOException {
		FileInputStream in = new FileInputStream(file);
		try {
			return computeHash(in, file.length());
		} finally {
			in.close();
		}
	}

	public static String computeHash(InputStream stream, long length) throws IOException {
		int chunkSizeForFile = (int) Math.min(HASH_CHUNK_SIZE, length);

		// buffer that will contain the head and the tail chunk, chunks will overlap if length is smaller than two chunks
		byte[] chunkBytes = new byte[(int) Math.min(2 * HASH_CHUNK_SIZE, length)];

		DataInputStream in = new DataInputStream(stream);

		// first chunk
		in.readFully(chunkBytes, 0, chunkSizeForFile);

		long position = chunkSizeForFile;
		long tailChunkPosition = length - chunkSizeForFile;

		// seek to position of the tail chunk, or not at all if length is smaller than two chunks
		while (position < tailChunkPosition && (position += in.skip(tailChunkPosition - position)) >= 0) {
		}

		// second chunk, or the rest of the data if length is smaller than two chunks
		in.readFully(chunkBytes, chunkSizeForFile, chunkBytes.length - chunkSizeForFile);

		long head = computeHashForChunk(ByteBuffer.wrap(chunkBytes, 0, chunkSizeForFile));
		long tail = computeHashForChunk(ByteBuffer.wrap(chunkBytes, chunkBytes.length - chunkSizeForFile, chunkSizeForFile));

		return String.format("%016x", length + head + tail);
	}

	private static long computeHashForChunk(ByteBuffer buffer) {
		LongBuffer longBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer();
		long hash = 0;

		while (longBuffer.hasRemaining()) {
			hash += longBuffer.get();
		}

		return hash;
	}

}
