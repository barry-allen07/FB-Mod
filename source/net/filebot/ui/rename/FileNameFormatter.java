
package net.filebot.ui.rename;

import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.Map;

import net.filebot.similarity.Match;
import net.filebot.vfs.FileInfo;

class FileNameFormatter implements MatchFormatter {

	@Override
	public boolean canFormat(Match<?, ?> match) {
		return match.getValue() instanceof File || match.getValue() instanceof FileInfo || match.getValue() instanceof String;
	}

	@Override
	public String preview(Match<?, ?> match) {
		return format(match, true, null);
	}

	@Override
	public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) {
		Object value = match.getValue();

		if (value instanceof File) {
			File file = (File) value;
			return extension ? file.getName() : getName(file);
		}

		if (value instanceof FileInfo) {
			FileInfo file = (FileInfo) value;
			return extension ? file.toFile().getName() : file.getName();
		}

		if (value instanceof String) {
			return extension ? value.toString() : getNameWithoutExtension(value.toString());
		}

		// cannot format value
		throw new IllegalArgumentException("Illegal value: " + value);
	}

}
