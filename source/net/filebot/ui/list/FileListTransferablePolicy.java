package net.filebot.ui.list;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.ui.transfer.FileTransferable.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.filebot.torrent.Torrent;
import net.filebot.ui.transfer.ArrayTransferable;
import net.filebot.ui.transfer.FileTransferablePolicy;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.web.Episode;

class FileListTransferablePolicy extends FileTransferablePolicy {

	private static final DataFlavor episodeArrayFlavor = ArrayTransferable.flavor(Episode.class);

	private Consumer<String> title;
	private Consumer<String> format;
	private Consumer<List<?>> model;

	public FileListTransferablePolicy(Consumer<String> title, Consumer<String> format, Consumer<List<?>> model) {
		this.title = title;
		this.format = format;
		this.model = model;
	}

	@Override
	public boolean accept(Transferable tr) throws Exception {
		return hasFileListFlavor(tr) || tr.isDataFlavorSupported(episodeArrayFlavor);
	}

	@Override
	public void handleTransferable(Transferable tr, TransferAction action) throws Exception {
		// handle episode data
		if (tr.isDataFlavorSupported(episodeArrayFlavor)) {
			Episode[] episodes = (Episode[]) tr.getTransferData((episodeArrayFlavor));
			if (episodes.length > 0) {
				format.accept(ListPanel.DEFAULT_EPISODE_FORMAT);
				title.accept(episodes[0].getSeriesName());
				model.accept(asList(episodes));
			}
			return;
		}

		// handle files
		super.handleTransferable(tr, action);
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	protected void clear() {
		format.accept("");
		title.accept("");
		model.accept(emptyList());
	}

	@Override
	protected void load(List<File> files, TransferAction action) throws IOException {
		// set title based on parent folder of first file
		title.accept(getFolderName(files.get(0).getParentFile()));

		if (containsOnly(files, TORRENT_FILES)) {
			loadTorrents(files);
		} else {
			// if only one folder was dropped, use its name as title
			if (files.size() == 1 && files.get(0).isDirectory()) {
				title.accept(getFolderName(files.get(0)));
			}

			// load all files from the given folders recursively up do a depth of 32
			format.accept(ListPanel.DEFAULT_FILE_FORMAT);
			model.accept(listFiles(files, FILES, HUMAN_NAME_ORDER));
		}
	}

	private void loadTorrents(List<File> files) throws IOException {
		List<Torrent> torrents = new ArrayList<Torrent>(files.size());
		for (File file : files) {
			torrents.add(new Torrent(file));
		}

		// set title
		if (torrents.size() > 0) {
			title.accept(getNameWithoutExtension(torrents.get(0).getName()));
		}

		// add torrent entries
		format.accept(ListPanel.DEFAULT_FILE_FORMAT);
		model.accept(torrents.stream().flatMap(t -> t.getFiles().stream()).collect(toList()));
	}

	@Override
	public String getFileFilterDescription() {
		return "Files, Folders and Torrents";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return ExtensionFileFilter.WILDCARD;
	}

}
