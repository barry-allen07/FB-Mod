
package net.filebot.web;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MovieFormat extends Format {

	public static final MovieFormat NameYear = new MovieFormat(true, true, true);

	private final boolean includeYear;
	private final boolean includePartIndex;
	private final boolean smart;

	public MovieFormat(boolean includeYear, boolean includePartIndex, boolean smart) {
		this.includeYear = includeYear;
		this.includePartIndex = includePartIndex;
		this.smart = smart;
	}

	@Override
	public StringBuffer format(Object obj, StringBuffer sb, FieldPosition pos) {
		// format episode object, e.g. Avatar (2009), Part 1
		Movie movie = (Movie) obj;

		sb.append(movie.getName());

		if (includeYear) {
			if (!smart || movie.getYear() > 0) {
				sb.append(' ').append('(').append(movie.getYear()).append(')');
			}
		}

		if (includePartIndex && movie instanceof MoviePart) {
			MoviePart part = (MoviePart) movie;

			if (!smart || part.partCount > 1) {
				sb.append(", Part ").append(part.partIndex);
			}
		}

		return sb;
	}

	private final Pattern moviePattern = Pattern.compile("([^\\p{Punct}]+?)[\\p{Punct}\\s]+(\\d{4})(?:[\\p{Punct}\\s]+|$)");
	private final Pattern partPattern = Pattern.compile("(?:Part|CD)\\D?(\\d)$", Pattern.CASE_INSENSITIVE);

	@Override
	public Movie parseObject(String source, ParsePosition pos) {
		String s = source;
		Matcher m;

		// extract part information first
		int partIndex = -1;
		int partCount = -1;
		if ((m = partPattern.matcher(s)).find()) {
			partIndex = Integer.parseInt(m.group(1));
			s = m.replaceFirst("");
		}

		// parse movie information
		if ((m = moviePattern.matcher(s)).matches()) {
			String name = m.group(1).trim();
			int year = Integer.parseInt(m.group(2));

			Movie movie = new Movie(name, year);
			if (partIndex >= 0) {
				movie = new MoviePart(movie, partIndex, partCount);
			}

			// did parse input
			pos.setIndex(source.length());
			return movie;
		}

		// failed to parse input
		pos.setErrorIndex(0);
		return null;
	}

	@Override
	public Movie parseObject(String source) throws ParseException {
		return (Movie) super.parseObject(source);
	}

}
