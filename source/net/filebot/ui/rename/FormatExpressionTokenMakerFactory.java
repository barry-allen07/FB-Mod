package net.filebot.ui.rename;

import static java.util.Collections.*;

import java.util.Set;

import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;

public class FormatExpressionTokenMakerFactory extends TokenMakerFactory {

	public static final String SYNTAX_STYLE_GROOVY_FORMAT_EXPRESSION = "text/groovy-format-expression";

	@Override
	public FormatExpressionTokenMaker getTokenMakerImpl(String key) {
		return new FormatExpressionTokenMaker();
	}

	@Override
	public Set<String> keySet() {
		return singleton(SYNTAX_STYLE_GROOVY_FORMAT_EXPRESSION);
	}

}
