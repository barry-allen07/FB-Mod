package net.filebot.platform.mac;

import static net.filebot.platform.mac.xattr.XAttrUtil.*;

import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;

import net.filebot.util.XattrView;

public class MacXattrView implements XattrView {

	private final String path;

	public MacXattrView(Path path) {
		// MacOS filesystem may require NFD unicode decomposition
		this.path = Normalizer.normalize(path.toString(), Form.NFD);
	}

	@Override
	public List<String> list() {
		return listXAttr(path);
	}

	@Override
	public String read(String key) {
		return getXAttr(path, key);
	}

	@Override
	public void write(String key, String value) {
		setXAttr(path, key, value);
	}

	@Override
	public void delete(String key) {
		removeXAttr(path, key);
	}

}
