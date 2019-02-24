package net.filebot.ui.rename;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.ExceptionUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.JComponent;

import net.filebot.CacheManager;
import net.filebot.HistorySpooler;
import net.filebot.LicenseError;
import net.filebot.MediaTypes;
import net.filebot.NativeRenameAction;
import net.filebot.ResourceManager;
import net.filebot.StandardRenameAction;
import net.filebot.UserFiles;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.similarity.Match;
import net.filebot.util.ui.ActionPopup;
import net.filebot.util.ui.ProgressMonitor;
import net.filebot.util.ui.ProgressMonitor.ProgressWorker;

class RenameAction extends AbstractAction {

	public static final String RENAME_ACTION = "RENAME_ACTION";

	private final RenameModel model;

	public RenameAction(RenameModel model) {
		this.model = model;
		resetValues();
	}

	public void resetValues() {
		putValue(RENAME_ACTION, StandardRenameAction.MOVE);
		putValue(NAME, "Rename");
		putValue(SMALL_ICON, ResourceManager.getIcon("action.rename"));
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		if (model.names().isEmpty() || model.files().isEmpty()) {
			log.info("Nothing to rename. New Names is empty. Please <Fetch Data> first.");
			return;
		}

		Window window = getWindow(evt.getSource());
		withWaitCursor(window, () -> {
			// flush all memory caches to disk (before starting any long running file system operations that might be cancelled by the user)
			CacheManager.getInstance().flushAll();

			// prepare rename map (abort and notify the user if background computation is still in progress)
			Map<File, File> renameMap = null;
			try {
				renameMap = validate(model.getRenameMap(), window);
			} catch (Exception e) {
				log.log(Level.WARNING, e::getMessage);
			}

			if (renameMap == null || renameMap.isEmpty()) {
				return;
			}

			List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>(model.matches());
			StandardRenameAction action = (StandardRenameAction) getValue(RENAME_ACTION);

			// start processing
			Map<File, File> renameLog = new LinkedHashMap<File, File>();

			//try {
				// require valid license for rename mode
				//LICENSE.check();

				if (useNativeShell() && NativeRenameAction.isSupported(action)) {
					// call on EDT
					NativeRenameWorker worker = new NativeRenameWorker(renameMap, renameLog, NativeRenameAction.valueOf(action.name()));
					worker.call(null, null, null);
				} else {
					// call and wait
					StandardRenameWorker worker = new StandardRenameWorker(renameMap, renameLog, action);
					String message = String.format("%s %d %s. This may take a while.", action.getDisplayVerb(), renameMap.size(), renameMap.size() == 1 ? "file" : "files");
					ProgressMonitor.runTask(action.getDisplayName(), message, worker).get();
				}
			//}
			/*catch (LicenseError e) {
				if (LICENSE.isFile()) {
					JComponent source = (JComponent) evt.getSource();
					createLicensePopup(e.getMessage(), evt).show(source, -3, source.getHeight() + 4);
				} else {
					log.severe(e::getMessage);
				}
			} catch (CancellationException e) {
				debug.finest(e::toString);
			} catch (Throwable e) {
				log.log(Level.SEVERE, e, cause(getRootCause(e)));
			}*/

			// abort if nothing happened
			if (renameLog.isEmpty()) {
				return;
			}

			log.info(String.format("%d files renamed.", renameLog.size()));

			// remove renamed matches
			renameLog.forEach((from, to) -> {
				model.matches().remove(model.files().indexOf(from));
			});

			HistorySpooler.getInstance().append(renameLog.entrySet());

			// store xattr
			storeMetaInfo(renameMap, matches);

			// delete empty folders
			if (action == StandardRenameAction.MOVE) {
				deleteEmptyFolders(renameLog);
			}
		});
	}

	private void storeMetaInfo(Map<File, File> renameMap, List<Match<Object, File>> matches) {
		// write metadata into xattr if xattr is enabled
		for (Match<Object, File> match : matches) {
			File file = match.getCandidate();
			Object info = match.getValue();
			File destination = renameMap.get(file);
			if (info != null && destination != null) {
				destination = resolve(file, destination);
				if (destination.isFile()) {
					String original = file.getName();
					debug.finest(format("Store xattr: [%s, %s] => %s", info, original, destination));
					xattr.setMetaInfo(destination, info, original);
				}
			}
		}
	}

	private void deleteEmptyFolders(Map<File, File> renameMap) {
		// collect empty folders and files in reverse order
		Set<File> deleteFiles = new TreeSet<File>();

		renameMap.forEach((s, d) -> {
			File sourceFolder = s.getParentFile();
			File destinationFolder = resolve(s, d).getParentFile();

			// destination folder is the source, or is inside the source folder
			if (d.getParentFile() == null || destinationFolder.getPath().startsWith(sourceFolder.getPath())) {
				return;
			}

			try {
				// guess affected folder depth
				int tailSize = listStructurePathTail(d.getParentFile()).size();

				for (int i = 0; i < tailSize && !isStructureRoot(sourceFolder); sourceFolder = sourceFolder.getParentFile(), i++) {
					File[] children = sourceFolder.listFiles();
					if (children == null || !stream(children).allMatch(f -> deleteFiles.contains(f) || isThumbnailStore(f))) {
						return;
					}

					stream(children).forEach(deleteFiles::add);
					deleteFiles.add(sourceFolder);
				}
			} catch (Exception e) {
				debug.warning(e::toString);
			}
		});

		// use system trash to delete left-behind empty folders / hidden files
		try {
			for (File file : deleteFiles) {
				if (file.exists()) {
					UserFiles.trash(file);
				}
			}
		} catch (Throwable e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}
	}

	private Map<File, File> validate(Map<File, File> renameMap, Window parent) {
		// rename map values as modifiable list
		List<File> destinationPathView = new AbstractList<File>() {

			private File[] keyIndex = renameMap.keySet().toArray(new File[0]);

			@Override
			public File get(int i) {
				return renameMap.get(keyIndex[i]);
			}

			@Override
			public File set(int i, File value) {
				return renameMap.put(keyIndex[i], value);
			}

			@Override
			public int size() {
				return keyIndex.length;
			}
		};

		if (ValidateDialog.validate(parent, destinationPathView)) {
			// ask for user permissions for output folders so we can check them
			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(parent, renameMap.entrySet().stream().flatMap(e -> Stream.of(e.getKey(), resolve(e.getKey(), e.getValue()))).collect(toList()))) {
					return emptyMap();
				}
			}

			if (ConflictDialog.check(parent, renameMap)) {
				return renameMap;
			}
		}

		// return empty list if validation was cancelled
		return emptyMap();
	}

/*	private ActionPopup createLicensePopup(String message, ActionEvent evt) {
		ActionPopup actionPopup = new ActionPopup("License Required", ResourceManager.getIcon("file.lock"));

		actionPopup.add(newAction("Paste License Key", ResourceManager.getIcon("license.import"), c -> {
			withWaitCursor(evt.getSource(), () -> {
				try {
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
						String clip = (String) clipboard.getData(DataFlavor.stringFlavor);
						configureLicense(clip);
						return;
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
				}

				log.info("The clipboard does not contain a license key. Please select and copy your license key first.");
			});
		}));

		actionPopup.add(newAction("Select License File", ResourceManager.getIcon("license.import"), c -> {
			withWaitCursor(evt.getSource(), () -> {
				List<File> files = UserFiles.FileChooser.AWT.showLoadDialogSelectFiles(false, false, null, MediaTypes.LICENSE_FILES, "Select License", evt);
				if (files.size() > 0) {
					configureLicense(files.get(0));
					return;
				}
			});
		}));

		actionPopup.add(newAction("Purchase License", ResourceManager.getIcon("license.purchase"), c -> {
			openURI(getPurchaseURL());
		}));

		actionPopup.setStatus(message);

		return actionPopup;
	} */

	protected static class StandardRenameWorker implements ProgressWorker<Map<File, File>> {

		private Map<File, File> renameMap;
		private Map<File, File> renameLog;

		private StandardRenameAction action;

		public StandardRenameWorker(Map<File, File> renameMap, Map<File, File> renameLog, StandardRenameAction action) {
			this.renameMap = renameMap;
			this.renameLog = renameLog;
			this.action = action;
		}

		@Override
		public Map<File, File> call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
			for (Entry<File, File> mapping : renameMap.entrySet()) {
				if (cancelled.get()) {
					return renameLog;
				}

				message.accept(mapping.getKey().getName());

				// rename file, throw exception on failure
				File source = mapping.getKey();
				File destination = resolve(mapping.getKey(), mapping.getValue());

				if (!equalsCaseSensitive(source, destination)) {
					action.rename(source, destination);
				}

				// remember successfully renamed matches for history entry and possible revert
				renameLog.put(mapping.getKey(), mapping.getValue());
			}

			return renameLog;
		}
	}

	protected static class NativeRenameWorker implements ProgressWorker<Map<File, File>> {

		private Map<File, File> renameMap;
		private Map<File, File> renameLog;

		private NativeRenameAction action;

		public NativeRenameWorker(Map<File, File> renameMap, Map<File, File> renameLog, NativeRenameAction action) {
			this.renameMap = renameMap;
			this.renameLog = renameLog;
			this.action = action;
		}

		@Override
		public Map<File, File> call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
			// prepare delta, ignore files already named as desired
			Map<File, File> renamePlan = new LinkedHashMap<File, File>();

			renameMap.forEach((from, to) -> {
				// resolve relative paths
				to = resolve(from, to);

				if (!equalsCaseSensitive(from, to)) {
					renamePlan.put(from, to);
				}
			});

			// call native shell move/copy
			try {
				action.rename(renamePlan);
			} catch (CancellationException e) {
				debug.finest(e::getMessage);
			}

			// confirm results
			renameMap.forEach((from, to) -> {
				// resolve relative paths
				if (resolve(from, to).exists()) {
					renameLog.put(from, to);
				}
			});

			return renameLog;
		}

	}

}
