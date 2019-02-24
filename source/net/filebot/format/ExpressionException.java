
package net.filebot.format;

import javax.script.ScriptException;

public class ExpressionException extends ScriptException {

	private final String message;

	public ExpressionException(String message, ScriptException cause) {
		super(message, cause.getFileName(), cause.getLineNumber(), cause.getColumnNumber());

		// can't set message via super constructor
		this.message = message;
	}

	public ExpressionException(Exception e) {
		super(e);

		// can't set message via super constructor
		this.message = e.getMessage();
	}

	@Override
	public String getMessage() {
		return message;
	}

}
