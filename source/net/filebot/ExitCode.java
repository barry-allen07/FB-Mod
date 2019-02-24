package net.filebot;

public interface ExitCode {

	public static final int SUCCESS = 0;

	public static final int ERROR = 1;

	public static final int BAD_LICENSE = 2;

	public static final int FAILURE = 3;

	public static final int DIE = 4;

	public static final int NOOP = 100;

}
