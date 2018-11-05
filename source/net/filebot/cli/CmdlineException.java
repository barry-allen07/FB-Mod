package net.filebot.cli;

public class CmdlineException extends RuntimeException {

	public CmdlineException(String message) {
		super(message);
	}

	public CmdlineException(String message, Throwable cause) {
		super(message, cause);
	}

}
