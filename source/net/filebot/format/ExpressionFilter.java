package net.filebot.format;

import static net.filebot.format.ExpressionFormat.*;

import java.security.AccessController;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

public class ExpressionFilter {

	private final String expression;
	private final CompiledScript compiledExpression;

	private Throwable lastException;

	public ExpressionFilter(String expression) throws ScriptException {
		this.expression = expression;
		this.compiledExpression = new SecureCompiledScript(compileScriptlet(expression));
	}

	public String getExpression() {
		return expression;
	}

	public Throwable getLastException() {
		return lastException;
	}

	public boolean matches(Object value) {
		return matches(new ExpressionBindings(value));
	}

	public boolean matches(Bindings bindings) {
		this.lastException = null;

		// use privileged bindings so we are not restricted by the script sandbox
		Bindings priviledgedBindings = PrivilegedInvocation.newProxy(Bindings.class, bindings, AccessController.getContext());

		// initialize script context with the privileged bindings
		ScriptContext context = new SimpleScriptContext();
		context.setBindings(priviledgedBindings, ScriptContext.GLOBAL_SCOPE);

		try {
			// evaluate user script
			Object value = compiledExpression.eval(context);

			// value as boolean
			return DefaultTypeTransformation.castToBoolean(value);
		} catch (Throwable e) {
			// ignore any and all scripting exceptions
			this.lastException = e;
		}

		return false;
	}

}
