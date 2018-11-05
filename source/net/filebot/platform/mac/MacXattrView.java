package net.filebot.platform.mac;

import static net.filebot.platform.mac.xattr.XAttrUtil.*;

import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;

public class MacXattrView {

	private final String path;

	public MacXattrView(Path path) {
		// MacOS filesystem may require NFD unicode decomposition
		this.path = Normalizer.normalize(path.toAbsolutePath().toString(), Form.NFD);
	}

	public List<String> list() {
		return listXAttr(path);
	}

	public String read(String key) {
		return getXAttr(path, key);
	}

	public void write(String key, String value) {
		setXAttr(path, key, value);
	}

	public void delete(String key) {
		removeXAttr(path, key);
	}

}
