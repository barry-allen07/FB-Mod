package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.List;

public class PlainFileXattrView implements XattrView {

	private static final String XATTR_FOLDER = System.getProperty("net.filebot.xattr.store", ".xattr");

	private final Path root;
	private final Path node;

	public PlainFileXattrView(Path path) throws IOException {
		root = path.getParent().resolve(XATTR_FOLDER);
		node = root.resolve(path.getFileName());
	}

	@Override
	public List<String> list() throws IOException {
		if (Files.isDirectory(node)) {
			return Files.list(node).map(Path::getFileName).map(Path::toString).collect(toList());
		}
		return emptyList();
	}

	@Override
	public String read(String key) throws IOException {
		try {
			return new String(Files.readAllBytes(node.resolve(key)), UTF_8);
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	@Override
	public void write(String key, String value) throws IOException {
		if (!Files.isDirectory(node)) {
			Files.createDirectories(node);

			// set Hidden on Windows
			if (Files.getFileStore(root).supportsFileAttributeView(DosFileAttributeView.class)) {
				Files.getFileAttributeView(root, DosFileAttributeView.class).setHidden(true);
			}
		}

		Files.write(node.resolve(key), value.getBytes(UTF_8));
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(node.resolve(key));
	}

}
