package net.filebot.cli;

public interface ScriptProvider {

	String getScript(String name) throws Exception;

}