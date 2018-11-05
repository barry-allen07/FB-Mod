package net.filebot.format;

public class BindingException extends RuntimeException {

	public BindingException(String message, Throwable cause) {
		super(message, cause);
	}

	public BindingException(Object binding, String innerMessage) {
		this(binding, innerMessage, null);
	}

	public BindingException(Object binding, String innerMessage, Throwable cause) {
		this(String.format("Binding \"%s\": %s", binding, innerMessage), cause);
	}

}
