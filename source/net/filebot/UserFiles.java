package net.filebot;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.similarity.Normalization.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

import javax.swing.JFileChooser;

import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.util.FileUtilities;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

public class UserFiles {

	public static void trash(File file) throws IOException {
		// use system trash if possible
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
			try {
				if (Desktop.getDesktop().moveToTrash(file)) {
					return;
				}
				debug.log(Level.WARNING, message("Failed to move file to trash", file));
			} catch (Exception e) {
				debug.log(Level.WARNING, e::toString);
			}
		}

		// delete permanently if necessary
		if (file.exists()) {
			FileUtilities.delete(file);
		}
	}

	public static void revealFiles(Collection<File> files) {
		// try to reveal file in folder
		if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
			files.stream().collect(groupingBy(File::getParentFile, LinkedHashMap::new, toList())).forEach((parent, children) -> {
				try {
					Desktop.getDesktop().browseFileDirectory(children.get(children.size() - 1));
				} catch (Exception e) {
					debug.log(Level.WARNING, e::toString);
				}
			});
			return;
		}

		// if we can't reveal the file in folder, just reveal the parent folder
		files.stream().map(it -> it.getParentFile()).distinct().forEach(it -> {
			try {
				Desktop.getDesktop().open(it);
			} catch (Exception e) {
				debug.log(Level.WARNING, e::toString);
			}
		});
	}

	private static FileChooser defaultFileChooser = getPreferredFileChooser();

	public static void setDefaultFileChooser(FileChooser fileChooser) {
		defaultFileChooser = fileChooser;
	}

	public static List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
		String defaultFileKey = ((folderMode && filter == null) || !(folderMode && filter != null && isShiftOrAltDown(evt))) ? KEY_OPEN_FOLDER : KEY_OPEN_FILE;
		List<File> files = defaultFileChooser.showLoadDialogSelectFiles(defaultFileKey == KEY_OPEN_FOLDER, multiSelection, getFileChooserDefaultFile(defaultFileKey, defaultFile), filter, title, evt);
		if (files.size() > 0) {
			setFileChooserDefaultFile(defaultFileKey, files.get(0));
		}
		return files;
	}

	public static File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
		File file = defaultFileChooser.showSaveDialogSelectFile(folderMode, getFileChooserDefaultFile(KEY_SAVE, defaultFile), title, evt);
		if (file != null) {
			setFileChooserDefaultFile(KEY_SAVE, file);
		}
		return file;
	}

	public static File showOpenDialogSelectFolder(File defaultFile, String title, ActionEvent evt) {
		List<File> folder = defaultFileChooser.showLoadDialogSelectFiles(true, false, defaultFile, null, title, evt);
		return folder.size() > 0 ? folder.get(0) : null;
	}

	private static final String PREF_KEY_PREFIX = "dialog.";
	private static final String KEY_OPEN_FILE = "open.file";
	private static final String KEY_OPEN_FOLDER = "open.folder";
	private static final String KEY_SAVE = "save.file";

	protected static File getFileChooserDefaultFile(String key, File userValue) {
		if (userValue != null && userValue.getParentFile() != null)
			return userValue;

		String path = Settings.forPackage(UserFiles.class).get(PREF_KEY_PREFIX + key);
		if (path == null || path.isEmpty())
			return userValue;

		if (userValue != null && userValue.getParentFile() == null)
			return new File(new File(path).getParentFile(), userValue.getName());

		return new File(path);
	}

	protected static void setFileChooserDefaultFile(String name, File file) {
		Settings.forPackage(UserFiles.class).put(PREF_KEY_PREFIX + name, file.getPath());
	}

	public enum FileChooser {

		Swing {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(title);
				chooser.setMultiSelectionEnabled(multiSelection);
				chooser.setFileSelectionMode(folderMode && filter == null ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);

				if (defaultFile != null) {
					if (defaultFile.isFile()) {
						chooser.setSelectedFile(defaultFile);
					} else if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						chooser.setCurrentDirectory(defaultFile.getParentFile());
					}
				}

				if (filter != null && !filter.acceptAny()) {
					chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(truncateText(filter.toString(), 80), filter.extensions()));
				}

				if (chooser.showOpenDialog(getWindow(evt.getSource())) == JFileChooser.APPROVE_OPTION) {
					if (chooser.getSelectedFiles().length > 0)
						return asList(chooser.getSelectedFiles());
					if (chooser.getSelectedFile() != null)
						return asList(chooser.getSelectedFile());
				}
				return asList(new File[0]);
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(title);
				chooser.setMultiSelectionEnabled(false);
				chooser.setFileSelectionMode(folderMode ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
				chooser.setSelectedFile(defaultFile);

				if (chooser.showSaveDialog(getWindow(evt.getSource())) != JFileChooser.APPROVE_OPTION) {
					return null;
				}
				return chooser.getSelectedFile();
			}
		},

		AWT {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				FileDialog fileDialog = createFileDialog(evt, title, FileDialog.LOAD, folderMode);
				fileDialog.setTitle(title);
				fileDialog.setMultipleMode(multiSelection);

				if (defaultFile != null) {
					if (folderMode && defaultFile.isDirectory()) {
						fileDialog.setDirectory(defaultFile.getPath());
					} else if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						fileDialog.setDirectory(defaultFile.getParentFile().getPath());
						fileDialog.setFile(defaultFile.getName());
					}
				}

				if (filter != null) {
					fileDialog.setFilenameFilter(filter);
				}

				fileDialog.setVisible(true);
				return asList(fileDialog.getFiles());
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				FileDialog fileDialog = createFileDialog(evt, title, FileDialog.SAVE, folderMode);
				fileDialog.setTitle(title);
				fileDialog.setMultipleMode(false);
				if (defaultFile != null) {
					if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
						fileDialog.setDirectory(defaultFile.getParentFile().getPath());
					}
					fileDialog.setFile(defaultFile.getName());
				}

				fileDialog.setVisible(true);
				File[] files = fileDialog.getFiles();
				return files.length > 0 ? files[0] : null;
			}

			public FileDialog createFileDialog(ActionEvent evt, String title, int mode, boolean fileDialogForDirectories) {
				// By default, the AWT File Dialog lets you choose a file. Under certain circumstances, however, it may be proper for you to choose a directory instead. If that is the case, set this property to allow for directory selection in a file dialog.
				System.setProperty("apple.awt.fileDialogForDirectories", String.valueOf(fileDialogForDirectories));

				if (evt.getSource() instanceof Frame) {
					return new FileDialog((Frame) evt.getSource(), title, mode);
				}
				if (evt.getSource() instanceof Dialog) {
					return new FileDialog((Dialog) evt.getSource(), title, mode);
				}

				Frame[] frames = Frame.getFrames();
				return new FileDialog(frames.length > 0 ? frames[0] : null, title, mode);
			}
		},

		COCOA {

			@Override
			public List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt) {
				// directly use NSOpenPanel for via Objective-C bridge for FILES_AND_DIRECTORIES mode
				if (folderMode && filter != null) {
					// call native NSOpenPanel openPanel via Objective-C bridge
					return MacAppUtilities.NSOpenPanel_openPanel_runModal(title, true, true, true, filter.acceptAny() ? null : filter.extensions());
				}

				// default to AWT implementation
				return AWT.showLoadDialogSelectFiles(folderMode, multiSelection, defaultFile, filter, title, evt);
			}

			@Override
			public File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt) {
				// default to AWT implementation
				return AWT.showSaveDialogSelectFile(folderMode, defaultFile, title, evt);
			}
		},

		JavaFX {

			@Override
			public List<File> showLoadDialogSelectFiles(final boolean folderMode, final boolean multiSelection, final File defaultFile, final ExtensionFileFilter filter, final String title, final ActionEvent evt) {
				return runAndWait(new Callable<List<File>>() {

					@Override
					public List<File> call() throws Exception {
						// show DirectoryChooser
						if (folderMode) {
							javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
							directoryChooser.setTitle(title);
							if (defaultFile != null && defaultFile.isDirectory()) {
								directoryChooser.setInitialDirectory(defaultFile);
							}

							File file = directoryChooser.showDialog(null);
							if (file != null)
								return singletonList(file);
							else
								return emptyList();
						}

						// show FileChooser
						javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
						fileChooser.setTitle(title);
						if (filter != null && !filter.acceptAny()) {
							String[] globFilter = filter.extensions();
							for (int i = 0; i < globFilter.length; i++) {
								globFilter[i] = "*." + globFilter[i];
							}
							fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(filter.toString(), globFilter));
						}

						if (defaultFile != null) {
							if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
								fileChooser.setInitialDirectory(defaultFile.getParentFile());
								fileChooser.setInitialFileName(defaultFile.getName());
							}
						}

						if (multiSelection) {
							List<File> files = fileChooser.showOpenMultipleDialog(null);
							if (files != null)
								return files;
						} else {
							File file = fileChooser.showOpenDialog(null);
							if (file != null)
								return singletonList(file);
						}
						return emptyList();
					}
				});
			}

			@Override
			public File showSaveDialogSelectFile(final boolean folderMode, final File defaultFile, final String title, final ActionEvent evt) {
				return runAndWait(new Callable<File>() {

					@Override
					public File call() throws Exception {
						javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
						fileChooser.setTitle(title);

						if (defaultFile != null) {
							if (defaultFile.getParentFile() != null && defaultFile.getParentFile().isDirectory()) {
								fileChooser.setInitialDirectory(defaultFile.getParentFile());
							}
							fileChooser.setInitialFileName(defaultFile.getName());
						}

						return fileChooser.showSaveDialog(null);
					}
				});
			}

			public <T> T runAndWait(Callable<T> c) {
				try {
					// run on FX Thread
					FutureTask<T> task = new FutureTask<T>(c);
					invokeJavaFX(task);
					return task.get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		public abstract List<File> showLoadDialogSelectFiles(boolean folderMode, boolean multiSelection, File defaultFile, ExtensionFileFilter filter, String title, ActionEvent evt);

		public abstract File showSaveDialogSelectFile(boolean folderMode, File defaultFile, String title, ActionEvent evt);

	}

}
