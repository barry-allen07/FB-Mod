package net.filebot.media;

import static java.util.stream.Collectors.*;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;

import net.filebot.ResourceManager;
import net.filebot.web.Datasource;

public class PlainFileMatcher implements Datasource {

	@Override
	public String getIdentifier() {
		return "file";
	}

	@Override
	public String getName() {
		return "Plain File";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.generic");
	}

	public Map<File, Object> match(Collection<File> files, boolean strict) {
		return files.stream().collect(toMap(f -> f, f -> f, (a, b) -> a, LinkedHashMap::new));
	}

}
