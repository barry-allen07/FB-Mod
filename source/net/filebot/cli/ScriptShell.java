package net.filebot.cli;

import static net.filebot.util.RegularExpressions.*;

import java.util.Map;
import java.util.ResourceBundle;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.codehaus.groovy.runtime.StackTraceUtils;

import groovy.lang.GroovyClassLoader;

public class ScriptShell {

	public static ScriptEngine createScriptEngine() {
		ResourceBundle bundle = ResourceBundle.getBundle(ScriptShell.class.getName());

		CompilerConfiguration config = new CompilerConfiguration();
		config.setScriptBaseClass(bundle.getString("scriptBaseClass"));

		// default imports
		ImportCustomizer imports = new ImportCustomizer();
		imports.addStarImports(COMMA.split(bundle.getString("starImport")));
		imports.addStaticStars(COMMA.split(bundle.getString("starStaticImport")));
		config.addCompilationCustomizers(imports);

		GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config);
		return new GroovyScriptEngineImpl(classLoader);
	}

	public static final String ARGV_BINDING_NAME = "args";
	public static final String SHELL_BINDING_NAME = "__shell";
	public static final String SHELL_CLI_BINDING_NAME = "__cli";
	public static final String SHELL_ARGS_BINDING_NAME = "__args";

	private final ScriptEngine engine;
	private final ScriptProvider scriptProvider;

	public ScriptShell(ScriptProvider scriptProvider, CmdlineInterface cli, Map<String, ?> globals) throws ScriptException {
		this.engine = createScriptEngine();
		this.scriptProvider = scriptProvider;

		// setup bindings
		Bindings bindings = engine.createBindings();
		bindings.putAll(globals);

		// bind API objects
		bindings.put(SHELL_BINDING_NAME, this);
		bindings.put(SHELL_CLI_BINDING_NAME, cli);

		// setup script context
		engine.getContext().setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
	}

	public Object evaluate(String script, Bindings bindings) throws Throwable {
		try {
			return engine.eval(script, bindings);
		} catch (Throwable e) {
			while (e.getClass() == ScriptException.class && e.getCause() != null) {
				e = e.getCause();
			}
			throw StackTraceUtils.deepSanitize(e); // make Groovy stacktrace human-readable
		}
	}

	public Object runScript(String name, Bindings bindings) throws Throwable {
		return evaluate(scriptProvider.getScript(name), bindings);
	}

}
