package net.filebot.ui.sfv;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.Settings.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import net.filebot.hash.HashType;
import net.filebot.hash.VerificationFileReader;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.ui.transfer.BackgroundFileTransferablePolicy;
import net.filebot.util.ExceptionUtilities;
import net.filebot.util.FileSet;

class ChecksumTableTransferablePolicy extends BackgroundFileTransferablePolicy<ChecksumCell> {

	private final ChecksumTable table;
	private final ChecksumTableModel model;
	private final ChecksumComputationService computationService;

	public ChecksumTableTransferablePolicy(ChecksumTable table, ChecksumComputationService checksumComputationService) {
		this.table = table;
		this.model = table.getModel();
		this.computationService = checksumComputationService;
	}

	@Override
	protected boolean accept(List<File> files) {
		return true;
	}

	@Override
	protected void clear() {
		super.clear();

		computationService.reset();
		model.clear();
	}

	@Override
	protected void handleInBackground(List<File> files, TransferAction action) {
		if (files.size() == 1 && getHashType(files.get(0)) != null) {
			model.setHashType(getHashType(files.get(0)));
		}
		super.handleInBackground(files, action);
	}

	@Override
	protected void process(List<ChecksumCell> chunks) {
		model.addAll(chunks);
	}

	@Override
	protected void process(Exception e) {
		log.log(Level.WARNING, ExceptionUtilities.getRootCauseMessage(e), e);
	}

	private final ThreadLocal<ExecutorService> executor = new ThreadLocal<ExecutorService>();
	private final ThreadLocal<VerificationTracker> verificationTracker = new ThreadLocal<VerificationTracker>();

	@Override
	protected void load(List<File> files, TransferAction action) throws IOException {
		// make sure we have access to the parent folder structure, not just the dropped file
		if (isMacSandbox()) {
			MacAppUtilities.askUnlockFolders(getWindow(table), files);
		}

		// initialize drop parameters
		executor.set(computationService.newExecutor());
		verificationTracker.set(new VerificationTracker(5));

		try {
			// handle single verification file drop
			if (containsOnly(files, VERIFICATION_FILES)) {
				for (File file : files) {
					loadVerificationFile(file, getHashType(file));
				}
				return;
			}

			// handle single folder drop
			if (files.size() == 1 && containsOnly(files, FOLDERS)) {
				for (File folder : files) {
					for (File file : getChildren(folder, NOT_HIDDEN, HUMAN_NAME_ORDER)) {
						load(file, null, folder);
					}
				}
				return;
			}

			// handle files and folders dropped from the same parent folder
			if (mapByFolder(files).size() == 1) {
				for (File file : files) {
					load(file, null, file.getParentFile());
				}
				return;
			}

			// handle all other drops and auto-detect common root folder from dropped fileset
			FileSet fileset = new FileSet();
			files.forEach(fileset::add);

			for (Entry<Path, List<Path>> it : fileset.getRoots().entrySet()) {
				File root = it.getKey().toFile();
				for (Path path : it.getValue()) {
					File relativeFile = path.toFile().getParentFile();
					File absoluteFile = new File(root, path.toString());
					load(absoluteFile, relativeFile, root);
				}
			}
		} catch (InterruptedException e) {
			// supposed to happen if background execution is aborted
		} finally {
			// shutdown executor after all tasks have been completed
			executor.get().shutdown();

			// remove drop parameters
			executor.remove();
			verificationTracker.remove();
		}
	}

	protected void loadVerificationFile(File file, HashType type) throws IOException, InterruptedException {
		VerificationFileReader parser = new VerificationFileReader(createTextReader(file), type.getFormat());

		try {
			// root for relative file paths in verification file
			File baseFolder = file.getParentFile();

			while (parser.hasNext()) {
				// make this possibly long-running operation interruptible
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}

				Entry<File, String> entry = parser.next();

				String name = normalizePathSeparators(entry.getKey().getPath());
				String hash = new String(entry.getValue());

				ChecksumCell correct = new ChecksumCell(name, file, singletonMap(type, hash));
				ChecksumCell current = createComputationCell(name, baseFolder, type);

				ChecksumCell[] columns = { correct, current };
				publish(columns);
			}
		} finally {
			parser.close();
		}
	}

	protected void load(File absoluteFile, File relativeFile, File root) throws IOException, InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}

		// ignore hidden files/folders
		if (absoluteFile.isHidden()) {
			return;
		}

		// add next name to relative path
		relativeFile = new File(relativeFile, absoluteFile.getName());

		if (absoluteFile.isDirectory()) {
			// load all files in the file tree
			for (File child : getChildren(absoluteFile, NOT_HIDDEN, HUMAN_NAME_ORDER)) {
				load(child, relativeFile, root);
			}
		} else {
			String name = normalizePathSeparators(relativeFile.getPath());

			// publish computation cell first
			ChecksumCell[] computeCell = { createComputationCell(name, root, model.getHashType()) };
			publish(computeCell);

			// publish verification cell, if we can
			Map<File, String> hashByVerificationFile = verificationTracker.get().getHashByVerificationFile(absoluteFile);

			for (Entry<File, String> entry : hashByVerificationFile.entrySet()) {
				HashType hashType = verificationTracker.get().getVerificationFileType(entry.getKey());

				ChecksumCell[] verifyCell = { new ChecksumCell(name, entry.getKey(), singletonMap(hashType, entry.getValue())) };
				publish(verifyCell);
			}
		}
	}

	protected ChecksumCell createComputationCell(String name, File root, HashType hash) {
		ChecksumCell cell = new ChecksumCell(name, root, new ChecksumComputationTask(new File(root, name), hash));

		// start computation task
		executor.get().execute(cell.getTask());

		return cell;
	}

	@Override
	public String getFileFilterDescription() {
		return "Folders and SFV Files";
	}

	@Override
	public List<String> getFileFilterExtensions() {
		return asList(VERIFICATION_FILES.extensions());
	}

	private static class VerificationTracker {

		private final Map<File, Integer> seen = new HashMap<File, Integer>();
		private final Map<File, Map<File, String>> cache = new HashMap<File, Map<File, String>>();
		private final Map<File, HashType> types = new HashMap<File, HashType>();

		private final int maxDepth;

		public VerificationTracker(int maxDepth) {
			this.maxDepth = maxDepth;
		}

		public Map<File, String> getHashByVerificationFile(File file) throws IOException {
			// cache all verification files
			File folder = file.getParentFile();
			int depth = 0;

			while (folder != null && depth <= maxDepth) {
				Integer seenLevel = seen.get(folder);

				if (seenLevel != null && seenLevel <= depth) {
					// we have completely seen this parent tree before
					break;
				}

				if (seenLevel == null) {
					// folder we have never encountered before
					for (File verificationFile : getChildren(folder, VERIFICATION_FILES)) {
						HashType hashType = getHashType(verificationFile);
						cache.put(verificationFile, importVerificationFile(verificationFile, hashType, verificationFile.getParentFile()));
						types.put(verificationFile, hashType);
					}
				}

				// update
				seen.put(folder, depth);

				// step down
				folder = folder.getParentFile();
				depth++;
			}

			// just return if we know we won't find anything
			if (cache.isEmpty()) {
				return emptyMap();
			}

			// search all cached verification files
			Map<File, String> result = new HashMap<File, String>(2);

			for (Entry<File, Map<File, String>> entry : cache.entrySet()) {
				String hash = entry.getValue().get(file);

				if (hash != null) {
					result.put(entry.getKey(), hash);
				}
			}

			return result;
		}

		public HashType getVerificationFileType(File verificationFile) {
			return types.get(verificationFile);
		}

		/**
		 * Completely read a verification file and resolve all relative file paths against a given base folder
		 */
		private Map<File, String> importVerificationFile(File verificationFile, HashType hashType, File baseFolder) throws IOException {
			VerificationFileReader parser = new VerificationFileReader(createTextReader(verificationFile), hashType.getFormat());
			Map<File, String> result = new HashMap<File, String>();

			try {
				while (parser.hasNext()) {
					Entry<File, String> entry = parser.next();

					// resolve relative path, the hash is probably a substring, so we compact it, for memory reasons
					result.put(new File(baseFolder, entry.getKey().getPath()), new String(entry.getValue()));
				}
			} finally {
				parser.close();
			}

			return result;
		}
	}

}
