package net.filebot.cli;

import java.io.File;
import java.io.IOException;

import net.filebot.RenameAction;

public class ExecutableRenameAction implements RenameAction {

	private final String executable;
	private final File directory;

	public ExecutableRenameAction(String executable, File directory) {
		this.executable = executable;
		this.directory = directory;
	}

	@Override
	public File rename(File from, File to) throws Exception {
		ProcessBuilder process = new ProcessBuilder(executable, from.getCanonicalPath(), getRelativePath(directory, to));
		process.directory(directory);
		process.inheritIO();

		int exitCode = process.start().waitFor();
		if (exitCode != 0) {
			throw new IOException(String.format("%s failed with exit code %d", process.command(), exitCode));
		}

		return null;
	}

	private String getRelativePath(File dir, File f) {
		return dir == null ? f.toString() : dir.toPath().relativize(f.toPath()).toString();
	}

	@Override
	public boolean canRevert() {
		return false;
	}

	@Override
	public String toString() {
		return executable;
	}

}
