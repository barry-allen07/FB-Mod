
package net.filebot;

import java.io.File;

public interface RenameAction {

	File rename(File from, File to) throws Exception;

	default boolean canRevert() {
		return true;
	}

}
