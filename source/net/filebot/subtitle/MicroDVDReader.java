package net.filebot.subtitle;

import static net.filebot.util.RegularExpressions.*;
import static net.filebot.util.StringUtilities.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MicroDVDReader extends SubtitleReader {

	private double fps = 23.976;

	public MicroDVDReader(Scanner scanner) {
		super(scanner);
	}

	@Override
	public SubtitleElement readNext() throws Exception {
		String line = scanner.nextLine();

		List<String> properties = new ArrayList<String>(2);
		int from = 0;

		while (from < line.length() && line.charAt(from) == '{') {
			int to = line.indexOf('}', from + 1);

			// no more properties
			if (to < from)
				break;

			// extract property
			properties.add(line.substring(from + 1, to));

			// skip property
			from = to + 1;
		}

		if (properties.size() < 2) {
			// ignore illegal lines
			return null;
		}

		int startFrame = Integer.parseInt(properties.get(0));
		int endFrame = Integer.parseInt(properties.get(1));
		String text = line.substring(from).trim();

		// cancel format markers
		text = text.replaceAll("\\{[^\\}]*\\}", "");

		if (startFrame == 1 && endFrame == 1) {
			// override fps
			fps = Double.parseDouble(text);

			// ignore line
			return null;
		}

		// translate '|' to new lines
		String[] lines = PIPE.split(text);

		// convert frame interval to time interval
		return new SubtitleElement(Math.round(startFrame * fps), Math.round(endFrame * fps), join(lines, "\n"));
	}

}
