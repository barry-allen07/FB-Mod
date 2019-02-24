package net.filebot.format;

import static net.filebot.Logging.*;

import java.io.File;
import java.io.FileFilter;
import java.util.function.Function;

import javax.script.ScriptException;

public class ExpressionFileFilter implements FileFilter {

	private ExpressionFilter filter;
	private Function<File, Object> match;

	public ExpressionFileFilter(String expression) throws ScriptException {
		// use file object as match object by default
		this(expression, f -> f);
	}

	public ExpressionFileFilter(String expression, Function<File, Object> match) throws ScriptException {
		this.filter = new ExpressionFilter(expression);
		this.match = match;
	}

	public ExpressionFilter getExpressionFilter() {
		return filter;
	}

	@Override
	public boolean accept(File f) {
		try {
			return filter.matches(new MediaBindingBean(match.apply(f), f));
		} catch (Exception e) {
			debug.warning("Filter expression failed: " + e);
		}
		return false;
	}

}
