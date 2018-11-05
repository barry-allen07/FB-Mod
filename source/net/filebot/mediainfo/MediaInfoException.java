package net.filebot.mediainfo;

import com.sun.jna.Platform;

public class MediaInfoException extends RuntimeException {

	public MediaInfoException(String message) {
		super(message);
	}

	public MediaInfoException(LinkageError e) {
		super(getLinkageErrorMessage(e), e);
	}

	private static String getLinkageErrorMessage(LinkageError e) {
		String name = Platform.isWindows() ? "MediaInfo.dll" : Platform.isMac() ? "libmediainfo.dylib" : "libmediainfo.so";
		String arch = System.getProperty("os.arch");
		String bit = Platform.is64Bit() ? "64-bit" : "32-bit";
		return String.format("Unable to load %s (%s) native library %s: %s", arch, bit, name, e.getMessage());
	}

}
