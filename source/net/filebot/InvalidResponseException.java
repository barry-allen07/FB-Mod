package net.filebot;

import java.io.IOException;

public class InvalidResponseException extends IOException {

	public InvalidResponseException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidResponseException(String message, String content, Throwable cause) {
		super(String.format("%s: %s: %s\n%s", message, cause.getClass().getSimpleName(), cause.getMessage(), content), cause);
	}

}
