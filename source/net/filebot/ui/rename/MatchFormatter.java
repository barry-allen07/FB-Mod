
package net.filebot.ui.rename;

import java.util.Map;

import net.filebot.similarity.Match;

public interface MatchFormatter {

	public boolean canFormat(Match<?, ?> match);

	public String preview(Match<?, ?> match);

	public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) throws Exception;

}
