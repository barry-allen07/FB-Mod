package net.filebot.cli;

public class ScriptDeath extends Throwable {

	public final int exitCode;

	public ScriptDeath(int exitCode, String message) {
		super(message);
		this.exitCode = exitCode;
	}

	public ScriptDeath(int exitCode, Throwable cause) {
		super(cause);
		this.exitCode = exitCode;
	}

	public int getExitCode() {
		return exitCode;
	}

}
