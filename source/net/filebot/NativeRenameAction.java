package net.filebot;

import static java.util.Collections.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.ShellAPI.SHFILEOPSTRUCT;

public enum NativeRenameAction implements RenameAction {

	MOVE, COPY;

	@Override
	public File rename(File src, File dst) {
		dst = resolve(src, dst);
		rename(singletonMap(src, dst));
		return dst;
	}

	public void rename(Map<File, File> map) {
		List<File> src = new ArrayList<File>(map.size());
		List<File> dst = new ArrayList<File>(map.size());

		map.forEach((from, to) -> {
			// resolve relative paths
			src.add(from);
			dst.add(resolve(from, to));
		});

		// call Windows MOVE / COPY dialog
		SHFileOperation(this, getPathArray(src), getPathArray(dst));
	}

	private static void SHFileOperation(NativeRenameAction action, String[] src, String[] dst) {
		// configure parameter structure
		SHFILEOPSTRUCT op = new SHFILEOPSTRUCT();

		op.wFunc = SHFileOperationFunction(action);
		op.fFlags = Shell32.FOF_MULTIDESTFILES | Shell32.FOF_NOCOPYSECURITYATTRIBS | Shell32.FOF_NOCONFIRMATION | Shell32.FOF_NOCONFIRMMKDIR;

		op.pFrom = op.encodePaths(src);
		op.pTo = op.encodePaths(dst);

		Shell32.INSTANCE.SHFileOperation(op);

		if (op.fAnyOperationsAborted) {
			throw new CancellationException(action.name() + " cancelled");
		}
	}

	private static int SHFileOperationFunction(NativeRenameAction action) {
		switch (action) {
		case MOVE:
			return ShellAPI.FO_MOVE;
		case COPY:
			return ShellAPI.FO_COPY;
		default:
			throw new UnsupportedOperationException("SHFileOperation not supported: " + action);
		}
	}

	private static String[] getPathArray(List<File> files) {
		return files.stream().map(File::getAbsolutePath).toArray(String[]::new);
	}

	public static boolean isSupported(StandardRenameAction action) {
		try {
			return Platform.isWindows() && (action == StandardRenameAction.MOVE || action == StandardRenameAction.COPY);
		} catch (Throwable e) {
			return false;
		}
	}

}
