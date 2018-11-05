
package net.filebot.vfs;

import java.nio.ByteBuffer;

public class MemoryFile {

	private final String path;

	private final ByteBuffer data;

	public MemoryFile(String path, ByteBuffer data) {
		// normalize folder separator
		this.path = path.replace('\\', '/');
		this.data = data;
	}

	public String getName() {
		return path.substring(path.lastIndexOf("/") + 1);
	}

	public String getPath() {
		return path;
	}

	public int size() {
		return data.remaining();
	}

	public ByteBuffer getData() {
		return data.duplicate();
	}

	@Override
	public String toString() {
		return path;
	}

}
