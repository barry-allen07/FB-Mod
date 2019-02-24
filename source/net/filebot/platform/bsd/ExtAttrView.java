package net.filebot.platform.bsd;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.List;

import net.filebot.util.ByteBufferOutputStream;
import net.filebot.util.XattrView;

public class ExtAttrView implements XattrView {

	private final String path;

	public ExtAttrView(Path path) {
		this.path = path.toString();
	}

	public List<String> list() throws IOException {
		return SPACE.splitAsStream(execute("lsextattr", "-q", "user", path)).map(String::trim).filter(s -> s.length() > 0).collect(toList());
	}

	public String read(String key) {
		try {
			return execute("getextattr", "-q", "user", key, path).toString().trim();
		} catch (IOException e) {
			return null;
		}
	}

	public void write(String key, String value) throws IOException {
		execute("setextattr", "-q", "user", key, value, path);
	}

	public void delete(String key) throws IOException {
		execute("rmextattr", "-q", "user", key, path);
	}

	protected CharSequence execute(String... command) throws IOException {
		Process process = new ProcessBuilder(command).redirectError(Redirect.INHERIT).start();

		try (ByteBufferOutputStream bb = new ByteBufferOutputStream(8 * 1024)) {
			bb.transferFully(process.getInputStream());

			int returnCode = process.waitFor();
			String output = UTF_8.decode(bb.getByteBuffer()).toString();

			// DEBUG
			debug.fine(format("Execute: %s", asList(command)));
			debug.finest(output);

			if (returnCode == 0) {
				return output;
			} else {
				throw new IOException(String.format("%s failed with exit code %d", command[0], returnCode));
			}
		} catch (InterruptedException e) {
			throw new IOException(String.format("%s timed out", command[0]), e);
		}
	}

}
