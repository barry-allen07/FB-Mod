
package net.filebot.subtitle;

import static java.util.regex.Pattern.*;
import static net.filebot.util.StringUtilities.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SubViewerReader extends SubtitleReader {

	private final DateFormat timeFormat = new SubtitleTimeFormat();
	private final Pattern newline = compile(quote("[br]"), CASE_INSENSITIVE);

	public SubViewerReader(Scanner scanner) {
		super(scanner);
	}

	@Override
	protected SubtitleElement readNext() throws Exception {
		// element starts with interval (e.g. 00:42:16.33,00:42:19.39)
		String[] interval = scanner.nextLine().split(",", 2);

		if (interval.length < 2 || interval[0].startsWith("[")) {
			// ignore property lines
			return null;
		}

		try {
			long t1 = timeFormat.parse(interval[0]).getTime();
			long t2 = timeFormat.parse(interval[1]).getTime();

			// translate [br] to new lines
			String[] lines = newline.split(scanner.nextLine());

			return new SubtitleElement(t1, t2, join(lines, "\n"));
		} catch (ParseException e) {
			// can't parse interval, ignore line
			return null;
		} catch (InputMismatchException e) {
			// can't parse interval, ignore line
			return null;
		}
	}
}
