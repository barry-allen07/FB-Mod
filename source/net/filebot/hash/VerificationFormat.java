
package net.filebot.hash;

import java.io.File;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationFormat extends Format {

	private final String hashTypeHint;

	public VerificationFormat() {
		this.hashTypeHint = "";
	}

	public VerificationFormat(String hashTypeHint) {
		this.hashTypeHint = hashTypeHint.isEmpty() ? "" : '?' + hashTypeHint.toUpperCase();
	}

	@Override
	public StringBuffer format(Object obj, StringBuffer sb, FieldPosition pos) {
		Entry<File, String> entry = (Entry<File, String>) obj;

		String path = entry.getKey().getPath();
		String hash = entry.getValue();

		return sb.append(format(path, hash));
	}

	public String format(String path, String hash) {
		// e.g. 1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA1*folder/file.txt
		return String.format("%s %s*%s", hash, hashTypeHint, path);
	}

	/**
	 * Pattern used to parse the lines of a md5 or sha1 file.
	 *
	 * <pre>
	 * Sample MD5:
	 * 50e85fe18e17e3616774637a82968f4c *folder/file.txt
	 * |           Group 1               |   Group 2   |
	 *
	 * Sample SHA-1:
	 * 1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA1*folder/file.txt
	 * |               Group 1                |       |   Group 2   |
	 * </pre>
	 */
	private final Pattern pattern = Pattern.compile("^(\\p{XDigit}+)\\s+(?:\\?\\w+)?\\*?(.+)$");

	@Override
	public Entry<File, String> parseObject(String line) throws ParseException {
		Matcher matcher = pattern.matcher(line);

		if (!matcher.find()) {
			throw new ParseException("Illegal input pattern", 0);
		}

		return entry(matcher.group(2), matcher.group(1));
	}

	@Override
	public Entry<File, String> parseObject(String line, ParsePosition pos) {
		throw new UnsupportedOperationException();
	}

	protected Entry<File, String> entry(String path, String hash) {
		return new SimpleImmutableEntry<File, String>(new File(path), hash);
	}

}
