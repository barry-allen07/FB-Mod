package net.filebot.cli;

public class ScriptDeath extends Throwable {

	public ScriptDeath(String message) {
		super(message);
	}

	public ScriptDeath(Throwable cause) {
		super(cause);
	}

}
