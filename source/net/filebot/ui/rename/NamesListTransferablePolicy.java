package net.filebot.ui.rename;

import static java.awt.datatransfer.DataFlavor.*;
import static java.util.stream.Collectors.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.ui.transfer.FileTransferable.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.RegularExpressions.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import net.filebot.hash.HashType;
import net.filebot.hash.VerificationFileReader;
import net.filebot.torrent.Torrent;
import net.filebot.ui.transfer.ArrayTransferable;
import net.filebot.ui.transfer.FileTransferablePolicy;
import net.filebot.util.FastFile;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.vfs.SimpleFileInfo;
import net.filebot.web.Episode;

class NamesListTransferablePolicy extends FileTransferablePolicy {

	private static final DataFlavor episodeArrayFlavor = ArrayTransferable.flavor(Episode.class);

	private final List<Object> model;

	public NamesListTransferablePolicy(List<Object> model) {
		this.model = model;
	}

	@Override
	protected void clear() {
		model.clear();
	}

	@Override
	public boolean accept(Transferable tr) throws Exception {
		return hasFileListFlavor(tr) || tr.isDataFlavorSupported(stringFlavor) || tr.isDataFlavorSupported(episodeArrayFlavor);
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	public void handleTransferable(Transferable tr, TransferAction action) throws Exception {
		if (action == TransferAction.PUT) {
			clear();
		}

		if (tr.isDataFlavorSupported(episodeArrayFlavor)) {
			// episode array transferable
			model.addAll(Arrays.asList((Episode[]) tr.getTransferData((episodeArrayFlavor))));
		} else if (hasFileListFlavor(tr)) {
			// file transferable
			load(getFilesFromTransferable(tr), action);
		} else if (tr.isDataFlavorSupported(stringFlavor)) {
			// string transferable
			load(tr.getTransferData(stringFlavor).toString());
		}
	}

	protected void load(String string) {
		List<String> values = NEWLINE.splitAsStream(string).map(String::trim).filter(s -> s.length() > 0).map(s -> normalizePathSeparators(s)).collect(toList());
		model.addAll(values);
	}

	@Override
	protected void load(List<File> files, TransferAction action) throws IOException {
		List<Object> values = new ArrayList<Object>();

		if (containsOnly(files, LIST_FILES)) {
			// list files
			loadListFiles(files, values);
		} else if (containsOnly(files, VERIFICATION_FILES)) {
			// verification files
			loadVerificationFiles(files, values);
		} else if (containsOnly(files, TORRENT_FILES)) {
			// torrent files
			loadTorrentFiles(files, values);
		} else {
			// load all files from the given folders recursively up do a depth of 32
			listFiles(files, FILES, HUMAN_NAME_ORDER).stream().map(FastFile::new).forEach(values::add);
		}

		model.addAll(values);
	}

	protected void loadListFiles(List<File> files, List<Object> values) throws IOException {
		for (File file : files) {
			// don't use new Scanner(File) because of BUG 6368019 (http://bugs.sun.com/view_bug.do?bug_id=6368019)
			Scanner scanner = new Scanner(createTextReader(file));

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine().trim();

				if (line.length() > 0) {
					values.add(line);
				}
			}

			scanner.close();
		}
	}

	protected void loadVerificationFiles(List<File> files, List<Object> values) throws IOException {
		for (File verificationFile : files) {
			HashType type = getHashType(verificationFile);

			// check if type is supported
			if (type == null)
				continue;

			// add all file names from verification file
			VerificationFileReader parser = new VerificationFileReader(createTextReader(verificationFile), type.getFormat());

			try {
				while (parser.hasNext()) {
					values.add(new SimpleFileInfo(parser.next().getKey().getName(), -1));
				}
			} finally {
				parser.close();
			}
		}
	}

	protected void loadTorrentFiles(List<File> files, List<Object> values) throws IOException {
		for (File file : files) {
			Torrent torrent = new Torrent(file);
			values.addAll(torrent.getFiles());
		}
	}

	@Override
	public String getFileFilterDescription() {
		return "Text Files, Verification Files, Torrent Files";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return Stream.of(VIDEO_FILES, SUBTITLE_FILES, AUDIO_FILES, LIST_FILES, TORRENT_FILES, VERIFICATION_FILES).map(ExtensionFileFilter::extensions).flatMap(Stream::of).collect(toList());
	}

}
