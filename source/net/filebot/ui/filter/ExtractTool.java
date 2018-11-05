package net.filebot.ui.filter;

import static net.filebot.Logging.*;
import static net.filebot.UserFiles.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import net.filebot.ResourceManager;
import net.filebot.archive.Archive;
import net.filebot.archive.FileMapper;
import net.filebot.cli.ConflictAction;
import net.filebot.util.FileUtilities;
import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.LoadingOverlayPane;
import net.filebot.util.ui.ProgressMonitor;
import net.filebot.util.ui.ProgressMonitor.ProgressWorker;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;
import net.miginfocom.swing.MigLayout;

class ExtractTool extends Tool<TableModel> {

	private JTable table = new JTable(new ArchiveEntryModel());

	public ExtractTool() {
		super("Archives");

		table.setAutoCreateRowSorter(true);
		table.setAutoCreateColumnsFromModel(true);
		table.setFillsViewportHeight(true);

		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		table.setBackground(Color.white);
		table.setGridColor(new Color(0xEEEEEE));
		table.setRowHeight(25);

		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setBorder(new SeparatorBorder(2, new Color(0, 0, 0, 90), GradientStyle.TOP_TO_BOTTOM, SeparatorBorder.Position.BOTTOM));

		setLayout(new MigLayout("insets 0, nogrid, fill", "align center", "[fill][pref!]"));
		add(new LoadingOverlayPane(tableScrollPane, this, "25px", "30px"), "grow, wrap");
		add(new JButton(extractAction), "gap top rel, gap bottom unrel");
	}

	@Override
	protected void setModel(TableModel model) {
		table.setModel(model);
	}

	@Override
	protected TableModel createModelInBackground(List<File> root) throws Exception {
		if (root.isEmpty()) {
			return new ArchiveEntryModel();
		}

		// ignore non-archives files and trailing multi-volume parts
		List<File> files = listFiles(root, Archive.VOLUME_ONE_FILTER, HUMAN_NAME_ORDER);
		List<ArchiveEntry> entries = new ArrayList<ArchiveEntry>();

		for (File file : files) {
			try (Archive archive = Archive.open(file)) {
				for (FileInfo it : archive.listFiles()) {
					entries.add(new ArchiveEntry(file, it));
				}
			}

			// unwind thread, if we have been cancelled
			if (Thread.interrupted()) {
				throw new CancellationException();
			}
		}

		return new ArchiveEntryModel(entries);
	}

	private Action extractAction = newAction("Extract All", ResourceManager.getIcon("package.extract"), evt -> {
		List<File> archives = ((ArchiveEntryModel) table.getModel()).getArchiveList();
		if (archives.isEmpty())
			return;

		File selectedFile = showOpenDialogSelectFolder(archives.get(0).getParentFile(), "Extract to ...", evt);
		if (selectedFile == null)
			return;

		ExtractWorker worker = new ExtractWorker(archives, selectedFile, null, true, ConflictAction.AUTO);
		ProgressMonitor.runTask("Extract", "Extracting files...", worker);
	});

	private static class ArchiveEntry {

		public final File archive;
		public final FileInfo entry;

		public ArchiveEntry(File archive, FileInfo entry) {
			this.archive = archive;
			this.entry = entry;
		}
	}

	private static class ArchiveEntryModel extends AbstractTableModel {

		private final ArchiveEntry[] data;

		public ArchiveEntryModel() {
			this.data = new ArchiveEntry[0];
		}

		public ArchiveEntryModel(Collection<ArchiveEntry> data) {
			this.data = data.toArray(new ArchiveEntry[data.size()]);
		}

		public List<File> getArchiveList() {
			Set<File> archives = new LinkedHashSet<File>();
			for (ArchiveEntry it : data) {
				archives.add(it.archive);
			}
			return new ArrayList<File>(archives);
		}

		@Override
		public int getRowCount() {
			return data.length;
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "File";
			case 1:
				return "Path";
			case 2:
				return "Size";
			}
			return null;
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return data[row].entry.getName();
			case 1:
				File root = new File(data[row].archive.getName());
				File prefix = data[row].entry.toFile().getParentFile();
				File path = (prefix == null) ? root : new File(root, prefix.getPath());
				return normalizePathSeparators(path.getPath());
			case 2:
				return FileUtilities.formatSize(data[row].entry.getLength());
			}

			return null;
		}

	}

	private static class ExtractWorker implements ProgressWorker<Void> {

		private final File[] archives;
		private final File outputFolder;

		private final FileFilter filter;
		private final boolean forceExtractAll;
		private final ConflictAction conflictAction;

		public ExtractWorker(Collection<File> archives, File outputFolder, FileFilter filter, boolean forceExtractAll, ConflictAction conflictAction) {
			this.archives = archives.toArray(new File[archives.size()]);
			this.outputFolder = outputFolder;
			this.filter = filter;
			this.forceExtractAll = forceExtractAll;
			this.conflictAction = conflictAction;
		}

		@Override
		public Void call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
			for (File file : archives) {
				try {
					// update progress dialog
					message.accept(String.format("Extracting %s", file.getName()));

					Archive archive = Archive.open(file);
					try {
						final FileMapper outputMapper = new FileMapper(outputFolder);

						final List<FileInfo> outputMapping = new ArrayList<FileInfo>();
						for (FileInfo it : archive.listFiles()) {
							File outputPath = outputMapper.getOutputFile(it.toFile());
							outputMapping.add(new SimpleFileInfo(outputPath.getPath(), it.getLength()));
						}

						Set<FileInfo> selection = new TreeSet<FileInfo>();
						for (FileInfo future : outputMapping) {
							if (filter == null || filter.accept(future.toFile())) {
								selection.add(future);
							}
						}

						// check if there is anything to extract at all
						if (selection.isEmpty()) {
							continue;
						}

						boolean skip = true;
						for (FileInfo future : filter == null || forceExtractAll ? outputMapping : selection) {
							if (conflictAction == ConflictAction.AUTO) {
								skip &= (future.toFile().exists() && future.getLength() == future.toFile().length());
							} else {
								skip &= (future.toFile().exists());
							}
						}

						if (!skip || conflictAction == ConflictAction.OVERRIDE) {
							if (filter == null || forceExtractAll) {
								// extract all files
								archive.extract(outputMapper.getOutputDir());
							} else {
								// extract files selected by the given filter
								archive.extract(outputMapper.getOutputDir(), outputMapper.newPathFilter(selection));
							}
						}
					} finally {
						archive.close();
					}
				} catch (Exception e) {
					log.log(Level.WARNING, "Failed to extract archive: " + file.getName(), e);
				}

				if (cancelled.get()) {
					throw new CancellationException("Extract cancelled");
				}
			}
			return null;
		}

	}

}
