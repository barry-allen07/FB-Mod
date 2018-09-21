
package net.filebot.ui.rename;

import static net.filebot.util.FileUtilities.*;

import java.util.Map;

import net.filebot.similarity.Match;
import net.filebot.web.Movie;
import net.filebot.web.MoviePart;

class MovieFormatter implements MatchFormatter {

	@Override
	public boolean canFormat(Match<?, ?> match) {
		return match.getValue() instanceof Movie;
	}

	@Override
	public String preview(Match<?, ?> match) {
		Movie movie = (Movie) match.getValue();
		StringBuilder name = new StringBuilder();

		// format as single-file or multi-part movie
		name.append(movie.getName()).append(" (").append(movie.getYear()).append(")");

		if (movie instanceof MoviePart) {
			MoviePart part = (MoviePart) movie;
			if (part.getPartCount() > 1) {
				name.append(".CD").append(part.getPartIndex());
			}
		}

		// remove path separators if the name contains any / or \
		return replacePathSeparators(name);
	}

	@Override
	public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) {
		return preview(match);
	}

}
