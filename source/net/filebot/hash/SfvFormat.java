
package net.filebot.hash;


import java.io.File;
import java.text.ParseException;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SfvFormat extends VerificationFormat {

	@Override
	public String format(String path, String hash) {
		// e.g folder/file.txt 970E4EF1
		return String.format("%s %s", path, hash);
	}


	/**
	 * Pattern used to parse the lines of a sfv file.
	 *
	 * <pre>
	 * Sample:
	 * folder/file.txt 970E4EF1
	 * |  Group 1    | | Gr.2 |
	 * </pre>
	 */
	private final Pattern pattern = Pattern.compile("^(.+)\\s+(\\p{XDigit}{8})$");


	@Override
	public Entry<File, String> parseObject(String line) throws ParseException {
		Matcher matcher = pattern.matcher(line);

		if (!matcher.matches()) {
			throw new ParseException("Illegal input pattern", 0);
		}

		return entry(matcher.group(1), matcher.group(2));
	}

}
