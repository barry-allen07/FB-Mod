package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;

public class DefaultXattrView implements XattrView {

	private final UserDefinedFileAttributeView fs;

	public DefaultXattrView(Path path) throws IOException {
		fs = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);

		// sanity check
		if (fs == null) {
			throw new IOException("UserDefinedFileAttributeView is not supported");
		}
	}

	@Override
	public List<String> list() throws IOException {
		return fs.list();
	}

	@Override
	public String read(String key) throws IOException {
		try {
			ByteBuffer buffer = ByteBuffer.allocate(fs.size(key));
			fs.read(key, buffer);
			buffer.flip();

			return UTF_8.decode(buffer).toString();
		} catch (FileSystemException e) {
			return null; // attribute does not exist
		}
	}

	@Override
	public void write(String key, String value) throws IOException {
		fs.write(key, UTF_8.encode(value));
	}

	@Override
	public void delete(String key) throws IOException {
		fs.delete(key);
	}

}
