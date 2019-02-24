package net.filebot.subtitle;

import static net.filebot.Logging.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class SubtitleReader implements Iterator<SubtitleElement>, Closeable {

	protected Scanner scanner;
	protected SubtitleElement current;

	public SubtitleReader(Scanner scanner) {
		this.scanner = scanner;
	}

	protected abstract SubtitleElement readNext() throws Exception;

	@Override
	public boolean hasNext() {
		// find next element
		while (current == null && scanner.hasNextLine()) {
			try {
				current = readNext();
			} catch (Exception e) {
				debug.finest(cause(e)); // log and ignore
			}
		}

		return current != null;
	}

	@Override
	public SubtitleElement next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}

		try {
			return current;
		} finally {
			current = null;
		}
	}

	@Override
	public void close() throws IOException {
		scanner.close();
	}

	public Stream<SubtitleElement> stream() {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED), false);
	}

}
