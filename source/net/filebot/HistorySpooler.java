package net.filebot;

import static java.nio.channels.Channels.*;
import static net.filebot.Logging.*;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;

import net.filebot.History.Element;

public final class HistorySpooler {

	private static final HistorySpooler instance = new HistorySpooler();

	public static HistorySpooler getInstance() {
		return instance;
	}

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(HistorySpooler.getInstance()::commit, "HistorySpoolerShutdownHook")); // commit session history on shutdown
	}

	private final File persistentHistoryFile = ApplicationFolder.AppData.resolve("history.xml");

	private int sessionHistoryTotalSize = 0;
	private int persistentHistoryTotalSize = -1;
	private boolean persistentHistoryEnabled = true;

	private final History sessionHistory = new History();

	public synchronized History getCompleteHistory() throws IOException {
		if (persistentHistoryFile.length() <= 0) {
			return new History(sessionHistory.sequences());
		}

		try (FileChannel channel = FileChannel.open(persistentHistoryFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
			try (FileLock lock = channel.lock()) {
				History history = History.importHistory(new CloseShieldInputStream(newInputStream(channel))); // keep JAXB from closing the stream
				history.addAll(sessionHistory.sequences());
				return history;
			}
		}
	}

	public synchronized void commit() {
		if (sessionHistory.sequences().isEmpty() || !persistentHistoryEnabled) {
			return;
		}

		try {
			try (FileChannel channel = FileChannel.open(persistentHistoryFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
				try (FileLock lock = channel.lock()) {
					History history = new History();

					// load existing history from previous sessions
					if (channel.size() > 0) {
						try {
							channel.position(0);
							history = History.importHistory(new CloseShieldInputStream(newInputStream(channel))); // keep JAXB from closing the stream
						} catch (Exception e) {
							debug.log(Level.SEVERE, "Failed to read history file", e);
						}
					}

					// write new combined history
					history.addAll(sessionHistory.sequences());

					channel.position(0);
					History.exportHistory(history, new CloseShieldOutputStream(newOutputStream(channel))); // keep JAXB from closing the stream
					channel.truncate(channel.position());

					sessionHistory.clear();
					persistentHistoryTotalSize = history.totalSize();
				}
			}
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to write history file", e);
		}
	}

	public synchronized void append(Map<File, File> elements) {
		append(elements.entrySet());
	}

	public synchronized void append(Iterable<Entry<File, File>> elements) {
		List<Element> sequence = new ArrayList<Element>();

		for (Entry<File, File> element : elements) {
			File k = element.getKey();
			File v = element.getValue();

			if (k != null && v != null) {
				sequence.add(new Element(k.getName(), v.getPath(), k.getParentFile()));
			}
		}

		if (sequence.size() > 0) {
			sessionHistory.add(sequence); // append to session history
			sessionHistoryTotalSize += sequence.size();
		}
	}

	public synchronized void append(History importHistory) {
		sessionHistory.merge(importHistory);
	}

	public synchronized History getSessionHistory() {
		return new History(sessionHistory.sequences());
	}

	public synchronized int getSessionHistoryTotalSize() {
		return sessionHistoryTotalSize;
	}

	public synchronized int getPersistentHistoryTotalSize() {
		return persistentHistoryTotalSize;
	}

	public synchronized void setPersistentHistoryEnabled(boolean persistentHistoryEnabled) {
		this.persistentHistoryEnabled = persistentHistoryEnabled;
	}

}
