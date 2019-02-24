package net.filebot.util;

import java.io.IOException;
import java.util.List;

public interface XattrView {

	public List<String> list() throws IOException;

	public String read(String key) throws IOException;

	public void write(String key, String value) throws IOException;

	public void delete(String key) throws IOException;

}
