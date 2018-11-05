
package net.filebot.hash;


import static net.filebot.Logging.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;


public class VerificationFileReader implements Iterator<Entry<File, String>>, Closeable {

	private final Scanner scanner;

	private final VerificationFormat format;

	private Entry<File, String> buffer;

	private int lineNumber = 0;


	public VerificationFileReader(Readable source, VerificationFormat format) {
		this.scanner = new Scanner(source);
		this.format = format;
	}


	@Override
	public boolean hasNext() {
		if (buffer == null) {
			// cache next entry
			buffer = nextEntry();
		}

		return buffer != null;
	}


	@Override
	public Entry<File, String> next() {
		// cache next entry
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		try {
			return buffer;
		} finally {
			// invalidate cache
			buffer = null;
		}
	}


	protected Entry<File, String> nextEntry() {
		Entry<File, String> entry = null;

		// get next valid entry
		while (entry == null && scanner.hasNextLine()) {
			String line = scanner.nextLine().trim();

			// ignore comments
			if (!isComment(line)) {
				try {
					entry = format.parseObject(line);
				} catch (ParseException e) {
					// log and ignore
					debug.warning(format("Illegal format on line %d: %s", lineNumber, line));
				}
			}

			lineNumber++;
		}

		return entry;
	}


	public int getLineNumber() {
		return lineNumber;
	}


	protected boolean isComment(String line) {
		return line.isEmpty() || line.startsWith(";");
	}


	@Override
	public void close() throws IOException {
		scanner.close();
	}


	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}
