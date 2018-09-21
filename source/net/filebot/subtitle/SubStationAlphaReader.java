
package net.filebot.subtitle;

import static java.util.Arrays.*;

import java.text.DateFormat;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SubStationAlphaReader extends SubtitleReader {

	private final DateFormat timeFormat = new SubtitleTimeFormat();
	private final Pattern newline = Pattern.compile(Pattern.quote("\\n"), Pattern.CASE_INSENSITIVE);
	private final Pattern tags = Pattern.compile("[{]\\\\[^}]+[}]");
	private final Pattern drawingTags = Pattern.compile("\\\\[p][0-4]"); // ignore drawing commands (http://docs.aegisub.org/3.2/ASS_Tags/#drawing-commands)

	private String[] format;
	private int formatIndexStart;
	private int formatIndexEnd;
	private int formatIndexText;

	public SubStationAlphaReader(Scanner scanner) {
		super(scanner);
	}

	private void readFormat() throws Exception {
		// read format line (e.g. Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text)
		String line = scanner.nextLine();
		String[] event = line.split(":", 2);

		// sanity check
		if (!event[0].equals("Format"))
			throw new InputMismatchException("Illegal format header: " + line);

		// read columns
		format = event[1].split(",");

		// normalize column names
		for (int i = 0; i < format.length; i++) {
			format[i] = format[i].trim().toLowerCase();
		}

		List<String> lookup = asList(format);
		formatIndexStart = lookup.indexOf("start");
		formatIndexEnd = lookup.indexOf("end");
		formatIndexText = lookup.indexOf("text");
	}

	@Override
	public SubtitleElement readNext() throws Exception {
		if (format == null) {
			// move to [Events] sections
			boolean found = false;

			while (!found && scanner.hasNextLine()) {
				found = scanner.nextLine().equals("[Events]");
			}

			if (!found) {
				throw new InputMismatchException("Cannot find [Events] section");
			}

			// read format header
			readFormat();
		}

		// read next dialogue line
		String[] event = scanner.nextLine().split(":", 2);

		// ignore non-dialog lines
		if (event.length < 2 || !event[0].equals("Dialogue"))
			return null;

		// extract information
		String[] values = event[1].split(",", format.length);

		long start = timeFormat.parse(values[formatIndexStart].trim()).getTime();
		long end = timeFormat.parse(values[formatIndexEnd].trim()).getTime();
		String text = values[formatIndexText].trim();

		// ignore drawing instructions
		if (drawingTags.matcher(text).find())
			return null;

		return new SubtitleElement(start, end, resolve(text));
	}

	protected String resolve(String text) {
		// remove tags
		text = tags.matcher(text).replaceAll("");

		// resolve line breaks
		return newline.matcher(text).replaceAll("\n");
	}

}
