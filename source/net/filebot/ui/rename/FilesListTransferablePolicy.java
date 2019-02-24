package net.filebot.ui.rename;

import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import net.filebot.media.MediaDetection;
import net.filebot.ui.transfer.BackgroundFileTransferablePolicy;
import net.filebot.util.ExceptionUtilities;
import net.filebot.util.FastFile;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

class FilesListTransferablePolicy extends BackgroundFileTransferablePolicy<File> {

	private final List<File> model;

	public FilesListTransferablePolicy(List<File> model) {
		this.model = model;
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	protected void clear() {
		model.clear();
	}

	@Override
	public void handleTransferable(Transferable tr, TransferAction action) throws Exception {
		if (action == TransferAction.LINK || action == TransferAction.PUT) {
			clear();
		}

		super.handleTransferable(tr, action);
	}

	@Override
	protected void load(List<File> files, TransferAction action) {
		// collect files recursively and eliminate duplicates
		Set<File> sink = new LinkedHashSet<File>(64, 4);

		// load files recursively by default
		load(files, action != TransferAction.LINK, sink);

		// use fast file to minimize system calls like length(), isDirectory(), isFile(), ...
		publish(sink.stream().map(FastFile::new).toArray(File[]::new));
	}

	private void load(List<File> files, boolean recursive, Collection<File> sink) {
		for (File f : files) {
			// load file paths from text files
			if (recursive && LIST_FILES.accept(f)) {
				try {
					List<File> paths = readLines(f).stream().filter(s -> s.length() > 0).map(path -> {
						try {
							File file = new File(path);
							return file.isAbsolute() && file.exists() ? file : null;
						} catch (Exception e) {
							return null; // ignore invalid file paths
						}
					}).filter(Objects::nonNull).collect(toList());

					if (paths.isEmpty()) {
						sink.add(f); // treat as simple text file
					} else {
						load(paths, false, sink); // add paths from text file
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, "Failed to read paths from text file: " + e.getMessage());
				}
			}

			// load normal files
			else if (!recursive || f.isFile() || MediaDetection.isDiskFolder(f)) {
				sink.add(f);
			}

			// load folders recursively
			else if (f.isDirectory()) {
				load(getChildren(f, NOT_HIDDEN, HUMAN_NAME_ORDER), true, sink); // FORCE NATURAL FILE ORDER
			}
		}
	}

	@Override
	public String getFileFilterDescription() {
		return "Files and Folders";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return ExtensionFileFilter.WILDCARD;
	}

	@Override
	protected void process(List<File> chunks) {
		model.addAll(chunks);
	}

	@Override
	protected void process(Exception e) {
		log.log(Level.WARNING, ExceptionUtilities.getRootCauseMessage(e), e);
	}

}
