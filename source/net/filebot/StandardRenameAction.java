package net.filebot;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.UserFiles.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import com.sun.jna.Platform;

import net.filebot.util.FileUtilities;

public enum StandardRenameAction implements RenameAction {

	MOVE {

		@Override
		public File rename(File from, File to) throws Exception {
			return FileUtilities.moveRename(from, to);
		}
	},

	COPY {

		@Override
		public File rename(File from, File to) throws Exception {
			return FileUtilities.copyAs(from, to);
		}
	},

	KEEPLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			File dest = FileUtilities.resolveDestination(from, to);

			// move file and the create a symlink to the new location via NIO.2
			try {
				Files.move(from.toPath(), dest.toPath());
				FileUtilities.createRelativeSymlink(from, dest, true);
			} catch (LinkageError e) {
				throw new Exception("Unsupported Operation: move, createSymbolicLink");
			}

			return dest;
		}
	},

	SYMLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			File dest = FileUtilities.resolveDestination(from, to);

			// create symlink via NIO.2
			try {
				return FileUtilities.createRelativeSymlink(dest, from, true);
			} catch (LinkageError e) {
				throw new Exception("Unsupported Operation: createSymbolicLink");
			}
		}
	},

	HARDLINK {

		@Override
		public File rename(File from, File to) throws Exception {
			File dest = FileUtilities.resolveDestination(from, to);

			// create hardlink via NIO.2
			try {
				return FileUtilities.createHardLinkStructure(dest, from);
			} catch (LinkageError e) {
				throw new Exception("Unsupported Operation: createLink");
			}
		}
	},

	CLONE {

		@Override
		public File rename(File from, File to) throws Exception {
			File dest = FileUtilities.resolveDestination(from, to);

			// clonefile or reflink requires filesystem that supports copy-on-write (e.g. apfs or btrfs)
			ProcessBuilder process = new ProcessBuilder();

			if (Platform.isMac()) {
				// -c copy files using clonefile
				process.command("cp", "-c", "-f", from.getPath(), dest.getPath());
			} else {
				// --reflink copy files using reflink
				process.command("cp", "--reflink", "--force", from.isDirectory() ? "--recursive" : "--no-target-directory", from.getPath(), dest.getPath());
			}

			process.directory(from.getParentFile());
			process.inheritIO();

			int exitCode = process.start().waitFor();
			if (exitCode != 0) {
				throw new IOException(String.format("%s failed with exit code %d", process.command(), exitCode));
			}

			return dest;
		}
	},

	DUPLICATE {

		@Override
		public File rename(File from, File to) throws Exception {
			try {
				return HARDLINK.rename(from, to);
			} catch (Exception e) {
				return COPY.rename(from, to);
			}
		}
	},

	TEST {

		@Override
		public File rename(File from, File to) throws IOException {
			return FileUtilities.resolve(from, to);
		}

		@Override
		public boolean canRevert() {
			return false;
		}
	};

	public String getDisplayName() {
		switch (this) {
		case MOVE:
			return "Rename";
		case COPY:
			return "Copy";
		case KEEPLINK:
			return "Keeplink";
		case SYMLINK:
			return "Symlink";
		case HARDLINK:
			return "Hardlink";
		case CLONE:
			return "Clone";
		case DUPLICATE:
			return "Hardlink or Copy";
		default:
			return "Test";
		}
	}

	public String getDisplayVerb() {
		switch (this) {
		case MOVE:
			return "Moving";
		case COPY:
			return "Copying";
		case KEEPLINK:
			return "Moving and symlinking";
		case SYMLINK:
			return "Symlinking";
		case HARDLINK:
			return "Hardlinking";
		case CLONE:
			return "Cloning";
		case DUPLICATE:
			return "Duplicating";
		default:
			return "Testing";
		}
	}

	public static List<String> names() {
		return stream(values()).map(Enum::name).collect(toList());
	}

	public static StandardRenameAction forName(String name) {
		for (StandardRenameAction action : values()) {
			if (action.name().equalsIgnoreCase(name)) {
				return action;
			}
		}

		throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
	}

	public static File revert(File current, File original) throws IOException {
		// do nothing if current and original path is exactly the same
		if (current.equals(original)) {
			return original;
		}

		// reverse move
		if (current.exists() && !original.exists()) {
			return FileUtilities.moveRename(current, original);
		}

		BasicFileAttributes currentAttr = Files.readAttributes(current.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		BasicFileAttributes originalAttr = Files.readAttributes(original.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

		// reverse symlink
		if (currentAttr.isSymbolicLink() && !originalAttr.isSymbolicLink()) {
			trash(current);
			return original;
		}

		// reverse keeplink
		if (!currentAttr.isSymbolicLink() && originalAttr.isSymbolicLink()) {
			trash(original);
			return FileUtilities.moveRename(current, original);
		}

		// reverse copy / hardlink
		if (currentAttr.isRegularFile() && originalAttr.isRegularFile()) {
			trash(current);
			return original;
		}

		// reverse folder copy
		if (currentAttr.isDirectory() && originalAttr.isDirectory()) {
			trash(original);
			return FileUtilities.moveRename(current, original);
		}

		throw new IllegalArgumentException(String.format("Cannot revert file: %s => %s", current, original));
	}

}
