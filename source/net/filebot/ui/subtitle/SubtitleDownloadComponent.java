package net.filebot.ui.subtitle;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.UserFiles.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.ObservableElementList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.TextFilterator;
import ca.odell.glazedlists.matchers.MatcherEditor;
import ca.odell.glazedlists.swing.DefaultEventListModel;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import ca.odell.glazedlists.swing.TextComponentMatcherEditor;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.subtitle.SubtitleFormat;
import net.filebot.ui.subtitle.SubtitlePackage.Download.Phase;
import net.filebot.ui.transfer.DefaultTransferHandler;
import net.filebot.util.ExceptionUtilities;
import net.filebot.util.ui.ListView;
import net.filebot.util.ui.SwingUI;
import net.filebot.vfs.MemoryFile;
import net.miginfocom.swing.MigLayout;

class SubtitleDownloadComponent extends JComponent {

	private EventList<SubtitlePackage> packages = new BasicEventList<SubtitlePackage>();

	private EventList<MemoryFile> files = new BasicEventList<MemoryFile>();

	private SubtitlePackageCellRenderer renderer = new SubtitlePackageCellRenderer();

	private JTextField filterEditor = new JTextField();

	public SubtitleDownloadComponent() {
		final JList packageList = new JList(createPackageListModel());
		packageList.setFixedCellHeight(32);
		packageList.setCellRenderer(renderer);

		// better selection behaviour
		DefaultEventSelectionModel<SubtitlePackage> packageSelection = new DefaultEventSelectionModel<SubtitlePackage>(packages);
		packageSelection.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION_DEFENSIVE);
		packageList.setSelectionModel(packageSelection);

		// context menu and fetch on double click
		packageList.addMouseListener(packageListMouseHandler);

		// file list view
		final JList fileList = new ListView(createFileListModel()) {

			@Override
			protected String convertValueToText(Object value) {
				MemoryFile file = (MemoryFile) value;
				return file.getName();
			}

			@Override
			protected Icon convertValueToIcon(Object value) {
				if (SUBTITLE_FILES.accept(value.toString()))
					return ResourceManager.getIcon("file.subtitle");

				return ResourceManager.getIcon("file.generic");
			}
		};

		// better selection behaviour
		DefaultEventSelectionModel<MemoryFile> fileSelection = new DefaultEventSelectionModel<MemoryFile>(files);
		fileSelection.setSelectionMode(ListSelection.MULTIPLE_INTERVAL_SELECTION_DEFENSIVE);
		fileList.setSelectionModel(fileSelection);

		// install dnd and clipboard export handler
		MemoryFileListExportHandler memoryFileExportHandler = new MemoryFileListExportHandler();
		fileList.setTransferHandler(new DefaultTransferHandler(null, memoryFileExportHandler, memoryFileExportHandler));

		fileList.setDragEnabled(true);
		fileList.addMouseListener(fileListMouseHandler);

		JButton clearButton = createImageButton(clearFilterAction);
		clearButton.setOpaque(false);

		setLayout(new MigLayout("nogrid, fill, novisualpadding", "[fill]", "[pref!][fill]"));

		add(new JLabel("Filter:"), "gap indent:push");
		add(filterEditor, "wmin 120px, gap rel");
		add(clearButton, "w pref!, h pref!");
		add(new JScrollPane(packageList), "newline, hmin 80px");

		JScrollPane scrollPane = new JScrollPane(fileList);
		scrollPane.setViewportBorder(new LineBorder(fileList.getBackground()));
		add(scrollPane, "newline, hmin max(80px, 30%)");

		// install fetch action
		SwingUI.installAction(packageList, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction("Fetch") {

			@Override
			public void actionPerformed(ActionEvent e) {
				fetch(packageList.getSelectedValuesList().toArray());
			}
		});

		// install open action
		SwingUI.installAction(fileList, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new AbstractAction("Open") {

			@Override
			public void actionPerformed(ActionEvent e) {
				open(fileList.getSelectedValuesList().toArray());
			}
		});
	}

	protected ListModel createPackageListModel() {
		// allow filtering by language name and subtitle name
		MatcherEditor<SubtitlePackage> matcherEditor = new TextComponentMatcherEditor<SubtitlePackage>(filterEditor, new TextFilterator<SubtitlePackage>() {

			@Override
			public void getFilterStrings(List<String> list, SubtitlePackage element) {
				list.add(element.getLanguage().getName());
				list.add(element.getName());
			}
		});

		// source list
		EventList<SubtitlePackage> source = getPackageModel();

		// filter list
		source = new FilterList<SubtitlePackage>(source, matcherEditor);

		// listen to changes (e.g. download progress)
		source = new ObservableElementList<SubtitlePackage>(source, GlazedLists.beanConnector(SubtitlePackage.class));

		// as list model
		return new DefaultEventListModel<SubtitlePackage>(source);
	}

	protected ListModel createFileListModel() {
		// source list
		EventList<MemoryFile> source = getFileModel();

		// sort by name
		source = new SortedList<MemoryFile>(source, new Comparator<MemoryFile>() {

			@Override
			public int compare(MemoryFile m1, MemoryFile m2) {
				return m1.getName().compareToIgnoreCase(m2.getName());
			}
		});

		// as list model
		return new DefaultEventListModel<MemoryFile>(source);
	}

	public void reset() {
		// cancel and reset download workers
		for (SubtitlePackage subtitle : packages) {
			subtitle.reset();
		}

		files.clear();
	}

	public EventList<SubtitlePackage> getPackageModel() {
		return packages;
	}

	public EventList<MemoryFile> getFileModel() {
		return files;
	}

	public void setLanguageVisible(boolean visible) {
		renderer.getLanguageLabel().setVisible(visible);
	}

	private void fetch(Object[] selection) {
		for (Object value : selection) {
			fetch((SubtitlePackage) value);
		}
	}

	private void fetch(final SubtitlePackage subtitle) {
		if (subtitle.getDownload().isStarted()) {
			// download has been started already
			return;
		}

		// listen to download
		subtitle.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getNewValue() == Phase.DONE) {
					try {
						files.addAll(subtitle.getDownload().get());
					} catch (CancellationException e) {
						// ignore cancellation
					} catch (Exception e) {
						log.log(Level.WARNING, ExceptionUtilities.getRootCauseMessage(e), e);

						// reset download
						subtitle.reset();
					}

					// listener no longer required
					subtitle.removePropertyChangeListener(this);
				}
			}
		});

		// enqueue worker
		subtitle.getDownload().start();
	}

	private void open(Object[] selection) {
		try {
			for (Object object : selection) {
				MemoryFile file = (MemoryFile) object;

				// only open subtitle files
				if (SUBTITLE_FILES.accept(file.getName())) {
					open(file);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e.getMessage(), e);
		}
	}

	private void open(MemoryFile file) throws IOException {
		SubtitleViewer viewer = new SubtitleViewer(file.getName());
		viewer.getTitleLabel().setText("Subtitle Viewer");
		viewer.getInfoLabel().setText(file.getPath());

		viewer.setData(decodeSubtitles(file));
		viewer.setVisible(true);
	}

	private void save(Object[] selection) {
		try {
			for (Object object : selection) {
				MemoryFile data = (MemoryFile) object;
				File destination = showSaveDialogSelectFile(false, new File(validateFileName(data.getName())), "Save Subtitles as ...", new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Save"));
				if (destination != null) {
					writeFile(data.getData(), destination);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e.getMessage(), e);
		}
	}

	private void export(Object[] selection) {
		try {
			File selectedOutputFolder = null;

			// default values
			SubtitleFormat selectedFormat = SubtitleFormat.SubRip;
			long selectedTimingOffset = 0;
			Charset selectedEncoding = UTF_8;

			// just use default values when we can't use a JFC with accessory component (also Swing OSX LaF doesn't seem to support JFileChooser::setAccessory)
			if (Settings.isMacApp()) {
				// COCOA || AWT
				selectedOutputFolder = showOpenDialogSelectFolder(null, "Export Subtitles to Folder (SubRip / UTF-8)", new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Export"));
			} else {
				// Swing
				SubtitleFileChooser sfc = new SubtitleFileChooser();
				sfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (sfc.showSaveDialog(getWindow(this)) == JFileChooser.APPROVE_OPTION) {
					selectedOutputFolder = sfc.getSelectedFile();
					selectedFormat = sfc.getSelectedFormat();
					selectedTimingOffset = sfc.getTimingOffset();
					selectedEncoding = sfc.getSelectedEncoding();
				}
			}

			if (selectedOutputFolder != null) {
				List<File> outputFiles = new ArrayList<File>();

				for (Object object : selection) {
					MemoryFile file = (MemoryFile) object;

					// normalize name and auto-adjust extension
					String name = validateFileName(getNameWithoutExtension(file.getName()));
					File destination = new File(selectedOutputFolder, name + "." + selectedFormat.getFilter().extension());

					SubtitleFormat targetFormat = selectedFormat.getFilter().accept(file.getName()) ? null : selectedFormat; // check if format conversion is necessary
					writeFile(exportSubtitles(file, targetFormat, selectedTimingOffset, selectedEncoding), destination);

					outputFiles.add(destination);
				}

				// reveal exported files
				revealFiles(outputFiles);
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e.getMessage(), e);
		}
	}

	private final Action clearFilterAction = new AbstractAction("Clear Filter", ResourceManager.getIcon("edit.clear")) {

		@Override
		public void actionPerformed(ActionEvent e) {
			filterEditor.setText("");
		}
	};

	private final MouseListener packageListMouseHandler = new MouseAdapter() {

		@Override
		public void mouseClicked(MouseEvent e) {
			// fetch on double click
			if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
				JList list = (JList) e.getSource();
				fetch(list.getSelectedValuesList().toArray());
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				JList list = (JList) e.getSource();

				int index = list.locationToIndex(e.getPoint());

				if (index >= 0 && !list.isSelectedIndex(index)) {
					// auto-select clicked element
					list.setSelectedIndex(index);
				}

				final Object[] selection = list.getSelectedValuesList().toArray();

				if (selection.length > 0) {
					JPopupMenu contextMenu = new JPopupMenu();

					JMenuItem item = contextMenu.add(new AbstractAction("Download", ResourceManager.getIcon("package.fetch")) {

						@Override
						public void actionPerformed(ActionEvent e) {
							fetch(selection);
						}
					});

					// disable menu item if all selected elements have been fetched already
					item.setEnabled(isPending(selection));

					contextMenu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		}

		private boolean isPending(Object[] selection) {
			for (Object value : selection) {
				SubtitlePackage subtitle = (SubtitlePackage) value;

				if (!subtitle.getDownload().isStarted()) {
					// pending download found
					return true;
				}
			}

			return false;
		}
	};

	private final MouseListener fileListMouseHandler = new MouseAdapter() {

		@Override
		public void mouseClicked(MouseEvent e) {
			// open on double click
			if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
				JList list = (JList) e.getSource();

				// open selection
				open(list.getSelectedValuesList().toArray());
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			maybeShowPopup(e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			maybeShowPopup(e);
		}

		private void maybeShowPopup(MouseEvent e) {
			if (e.isPopupTrigger()) {
				JList list = (JList) e.getSource();

				int index = list.locationToIndex(e.getPoint());

				if (index >= 0 && !list.isSelectedIndex(index)) {
					// auto-select clicked element
					list.setSelectedIndex(index);
				}

				Object[] selection = list.getSelectedValuesList().toArray();
				if (selection.length > 0) {
					JPopupMenu contextMenu = new JPopupMenu();
					contextMenu.add(newAction("Preview", ResourceManager.getIcon("action.find"), evt -> open(selection))); // Open
					contextMenu.add(newAction("Save As...", ResourceManager.getIcon("action.save"), evt -> save(selection))); // Save As...
					contextMenu.add(newAction("Export...", ResourceManager.getIcon("action.export"), evt -> export(selection))); // Export...
					contextMenu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		}

	};

}
