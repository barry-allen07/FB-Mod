package net.filebot.ui.subtitle;

import static javax.swing.BorderFactory.*;
import static javax.swing.JOptionPane.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URI;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.ResourceManager;
import net.filebot.WebServices;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.subtitle.SubtitleMetrics;
import net.filebot.subtitle.SubtitleNaming;
import net.filebot.util.ui.AbstractBean;
import net.filebot.util.ui.DashedSeparator;
import net.filebot.util.ui.EmptySelectionModel;
import net.filebot.util.ui.LinkButton;
import net.filebot.util.ui.RoundBorder;
import net.filebot.vfs.MemoryFile;
import net.filebot.web.SubtitleDescriptor;
import net.filebot.web.SubtitleProvider;
import net.filebot.web.VideoHashSubtitleService;
import net.miginfocom.swing.MigLayout;

class SubtitleAutoMatchDialog extends JDialog {

	private static final Color hashMatchColor = new Color(0xFAFAD2); // LightGoldenRodYellow
	private static final Color nameMatchColor = new Color(0xFFEBCD); // BlanchedAlmond
	private final JPanel hashMatcherServicePanel = createServicePanel(hashMatchColor);
	private final JPanel nameMatcherServicePanel = createServicePanel(nameMatchColor);

	private final List<SubtitleServiceBean> services = new ArrayList<SubtitleServiceBean>();
	private final JTable subtitleMappingTable = createTable();

	private final JComboBox<SubtitleNaming> preferredSubtitleNaming = new JComboBox<SubtitleNaming>(SubtitleNaming.values());

	private ExecutorService queryService;
	private ExecutorService downloadService;

	public SubtitleAutoMatchDialog(Window owner) {
		super(owner, "Download Subtitles", ModalityType.DOCUMENT_MODAL);
		preferredSubtitleNaming.setSelectedItem(SubtitleNaming.MATCH_VIDEO_ADD_LANGUAGE_TAG);

		JComponent content = (JComponent) getContentPane();
		content.setLayout(new MigLayout("fill, insets 12 15 7 15, nogrid, novisualpadding", "", "[fill][pref!]"));

		content.add(new JScrollPane(subtitleMappingTable), "grow, wrap");
		content.add(hashMatcherServicePanel, "gap after rel");
		content.add(nameMatcherServicePanel, "gap after indent*2");

		preferredSubtitleNaming.setBorder(createCompoundBorder(createTitledBorder("Subtitle Naming"), preferredSubtitleNaming.getBorder()));
		content.add(new JLabel(), "grow"); // SPACER
		content.add(preferredSubtitleNaming, "gap after indent*2");

		content.add(new JButton(downloadAction), "tag ok");
		content.add(new JButton(finishAction), "tag cancel");
	}

	protected JPanel createServicePanel(Color color) {
		JPanel panel = new JPanel(new MigLayout("hidemode 3, novisualpadding"));
		panel.setBorder(new RoundBorder());
		panel.setOpaque(false);
		panel.setBackground(color);
		panel.setVisible(false);
		return panel;
	}

	protected JTable createTable() {
		JTable table = new JTable(new SubtitleMappingTableModel());
		table.setDefaultRenderer(SubtitleMapping.class, new SubtitleMappingOptionRenderer());

		table.setRowHeight(24);
		table.setIntercellSpacing(new Dimension(5, 5));

		table.setBackground(Color.white);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);

		JComboBox editor = new SimpleComboBox(ResourceManager.getIcon("action.select"));
		editor.setRenderer(new SubtitleOptionRenderer(true));

		// disable selection
		table.setSelectionModel(new EmptySelectionModel());
		editor.setFocusable(false);

		table.setDefaultEditor(SubtitleMapping.class, new DefaultCellEditor(editor) {

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
				JComboBox editor = (JComboBox) super.getTableCellEditorComponent(table, value, isSelected, row, column);
				SubtitleMapping mapping = (SubtitleMapping) value;

				DefaultComboBoxModel model = new DefaultComboBoxModel(mapping.getOptions());
				model.addElement(null); // option to cancel selection

				editor.setModel(model);
				editor.setSelectedItem(mapping.getSelectedOption());
				return editor;
			}
		});

		return table;
	}

	public void setVideoFiles(File[] videoFiles) {
		subtitleMappingTable.setModel(new SubtitleMappingTableModel(videoFiles));
	}

	public void addSubtitleService(VideoHashSubtitleService service) {
		addSubtitleService(new VideoHashSubtitleServiceBean(service), hashMatcherServicePanel);
	}

	public void addSubtitleService(SubtitleProvider service) {
		addSubtitleService(new SubtitleProviderBean(service, this), nameMatcherServicePanel);
	}

	protected void addSubtitleService(final SubtitleServiceBean service, final JPanel servicePanel) {
		LinkButton component = new LinkButton(service.getDescription(), null, ResourceManager.getIcon("database.go"), service.getLink());
		component.setBorder(createEmptyBorder());
		component.setVisible(false);

		service.addPropertyChangeListener(evt -> {
			if (service.getState() == StateValue.STARTED) {
				component.setIcon(ResourceManager.getIcon("database.go"));
			} else {
				component.setIcon(ResourceManager.getIcon(service.getError() == null ? "database.ok" : "database.error"));
			}

			component.setVisible(true);
			component.setToolTipText(String.format("%s: %s", service.getName(), service.getError() == null ? service.getState().toString().toLowerCase() : service.getError().getMessage()));
			servicePanel.setVisible(true);
			servicePanel.getParent().revalidate();
		});

		services.add(service);
		servicePanel.add(component);
	}

	public void startQuery(Locale locale) {
		SubtitleMappingTableModel mappingModel = (SubtitleMappingTableModel) subtitleMappingTable.getModel();
		QueryTask queryTask = new QueryTask(services, mappingModel.getVideoFiles(), locale, SubtitleAutoMatchDialog.this) {

			@Override
			protected void process(List<Map<File, List<SubtitleDescriptorBean>>> sequence) {
				for (Map<File, List<SubtitleDescriptorBean>> subtitles : sequence) {
					// update subtitle options
					for (SubtitleMapping subtitleMapping : mappingModel) {
						List<SubtitleDescriptorBean> options = subtitles.get(subtitleMapping.getVideoFile());

						if (options != null && options.size() > 0) {
							subtitleMapping.addOptions(options);
						}
					}

					// make subtitle column visible
					if (subtitles.size() > 0) {
						mappingModel.setOptionColumnVisible(true);
					}
				}
			}

			@Override
			protected void done() {
				SwingUtilities.invokeLater(mappingModel::fireTableStructureChanged); // make sure UI is refershed after completion
			}
		};

		queryService = Executors.newSingleThreadExecutor();
		queryService.submit(queryTask);
	}

	private Boolean showConfirmReplaceDialog(List<?> files) {
		JList existingFilesComponent = new JList(files.toArray()) {

			@Override
			public Dimension getPreferredScrollableViewportSize() {
				// adjust component size
				return new Dimension(80, 50);
			}
		};

		Object[] message = new Object[] { "Replace existing subtitle files?", new JScrollPane(existingFilesComponent) };
		Object[] options = new Object[] { "Replace All", "Skip All", "Cancel" };
		JOptionPane optionPane = new JOptionPane(message, WARNING_MESSAGE, YES_NO_CANCEL_OPTION, null, options);

		// display option dialog
		optionPane.createDialog(SubtitleAutoMatchDialog.this, "Replace").setVisible(true);

		// replace all
		if (options[0] == optionPane.getValue())
			return true;

		// skip all
		if (options[1] == optionPane.getValue())
			return false;

		// cancel
		return null;
	}

	private final Action downloadAction = new AbstractAction("Download", ResourceManager.getIcon("dialog.continue")) {

		@Override
		public void actionPerformed(ActionEvent evt) {
			// disable any active cell editor
			if (subtitleMappingTable.getCellEditor() != null) {
				subtitleMappingTable.getCellEditor().stopCellEditing();
			}

			// don't allow restart of download as long as there are still unfinished download tasks
			if (downloadService != null && !downloadService.isTerminated()) {
				return;
			}

			final SubtitleMappingTableModel mappingModel = (SubtitleMappingTableModel) subtitleMappingTable.getModel();

			// make sure we have access to the parent folder structure, not just the dropped file
			if (isMacSandbox()) {
				MacAppUtilities.askUnlockFolders(getWindow(evt.getSource()), mappingModel.getVideoFiles());
			}

			// collect the subtitles that will be fetched
			List<DownloadTask> downloadQueue = new ArrayList<DownloadTask>();

			for (final SubtitleMapping mapping : mappingModel) {
				SubtitleDescriptorBean subtitleBean = mapping.getSelectedOption();

				if (subtitleBean != null && subtitleBean.getState() == null) {
					downloadQueue.add(new DownloadTask(mapping.getVideoFile(), subtitleBean, (SubtitleNaming) preferredSubtitleNaming.getSelectedItem()) {

						@Override
						protected void done() {
							try {
								mapping.setSubtitleFile(get());
							} catch (Exception e) {
								debug.log(Level.WARNING, e.getMessage(), e);
							}
						}
					});
				}
			}

			// collect downloads that will override a file
			List<DownloadTask> confirmReplaceDownloadQueue = new ArrayList<DownloadTask>();
			List<String> existingFiles = new ArrayList<String>();

			for (DownloadTask download : downloadQueue) {
				// target destination may not be known until files are extracted from archives
				File target = download.getDestination(null);

				if (target != null && target.exists()) {
					confirmReplaceDownloadQueue.add(download);
					existingFiles.add(target.getName());
				}
			}

			// confirm replace
			if (confirmReplaceDownloadQueue.size() > 0) {
				Boolean option = showConfirmReplaceDialog(existingFiles);

				// abort the operation altogether
				if (option == null) {
					return;
				}

				// don't replace any files
				if (option == false) {
					downloadQueue.removeAll(confirmReplaceDownloadQueue);
				}
			}

			// start download
			if (downloadQueue.size() > 0) {
				downloadService = Executors.newSingleThreadExecutor();

				for (DownloadTask downloadTask : downloadQueue) {
					downloadTask.getSubtitleBean().setState(StateValue.PENDING);
					downloadService.execute(downloadTask);
				}

				// terminate after all downloads have been completed
				downloadService.shutdown();
			}
		}
	};

	private final Action finishAction = new AbstractAction("Close", ResourceManager.getIcon("dialog.cancel")) {

		@Override
		public void actionPerformed(ActionEvent evt) {
			if (queryService != null) {
				queryService.shutdownNow();
			}

			if (downloadService != null) {
				downloadService.shutdownNow();
			}

			setVisible(false);
			dispose();
		}
	};

	private static class SubtitleMappingOptionRenderer extends DefaultTableCellRenderer {

		private final JComboBox optionComboBox = new SimpleComboBox(ResourceManager.getIcon("action.select"));

		public SubtitleMappingOptionRenderer() {
			optionComboBox.setBackground(Color.white);
			optionComboBox.setRenderer(new SubtitleOptionRenderer(false));
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			SubtitleMapping mapping = (SubtitleMapping) value;
			SubtitleDescriptorBean subtitleBean = mapping != null ? mapping.getSelectedOption() : null;

			// render combobox for subtitle options
			if (mapping != null && mapping.isEditable() && subtitleBean != null) {
				optionComboBox.setModel(new DefaultComboBoxModel(new Object[] { subtitleBean }));
				return optionComboBox;
			}

			// render status label
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setForeground(table.getForeground());

			if (subtitleBean == null) {
				if (mapping != null && mapping.getOptions().length == 0) {
					// no subtitles found
					setText("No subtitles found");
					setIcon(null);
					setForeground(Color.gray);
				} else {
					// no subtitles found
					setText("No subtitles selected");
					setIcon(null);
					setForeground(Color.gray);
				}
			} else if (subtitleBean.getState() == StateValue.PENDING) {
				// download in the queue
				setText(subtitleBean.getText());
				setIcon(ResourceManager.getIcon("worker.pending"));
			} else if (subtitleBean.getState() == StateValue.STARTED) {
				// download in progress
				setText(subtitleBean.getText());
				setIcon(ResourceManager.getIcon("action.fetch"));
			} else if (mapping != null && mapping.getSubtitleFile() != null) {
				// download complete
				setText(mapping.getSubtitleFile().getName());
				setIcon(ResourceManager.getIcon("status.ok"));
			} else {
				setText(null);
				setIcon(null);
			}

			return this;
		}
	}

	private static class SubtitleOptionRenderer extends DefaultListCellRenderer {

		private final Border padding = createEmptyBorder(3, 3, 3, 3);
		private final boolean isEditor;

		public SubtitleOptionRenderer(boolean isEditor) {
			this.isEditor = isEditor;
		}

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
			setBorder(padding);

			SubtitleDescriptorBean subtitleBean = (SubtitleDescriptorBean) value;

			if (isEditor && index == list.getModel().getSize() - 2) {
				setBorder(new CompoundBorder(new DashedSeparator(10, 4, Color.lightGray, list.getBackground()), getBorder())); // this element is always the last one
			}

			if (value == null) {
				setText("Cancel selection");
				setIcon(ResourceManager.getIcon("dialog.cancel"));
			} else {
				if (subtitleBean.getError() == null) {
					setText(subtitleBean.getText());
					setIcon(subtitleBean.getIcon());
				} else {
					setText(String.format("%s (%s)", subtitleBean.getError(), subtitleBean.getText()));
					setIcon(ResourceManager.getIcon("status.warning"));
				}

				if (!isSelected) {
					float f = subtitleBean.getMatchProbability();
					if (f < 1) {
						setOpaque(true);
						setBackground(nameMatchColor);
					}
					if (f < 0.9f) {
						setOpaque(true);
						setBackground(derive(Color.RED, (1 - f) * 0.5f));
					}
				}
			}

			return this;
		}
	}

	private static class SubtitleMappingTableModel extends AbstractTableModel implements Iterable<SubtitleMapping> {

		private final SubtitleMapping[] data;

		private boolean optionColumnVisible = false;

		public SubtitleMappingTableModel(File... videoFiles) {
			data = new SubtitleMapping[videoFiles.length];

			for (int i = 0; i < videoFiles.length; i++) {
				data[i] = new SubtitleMapping(videoFiles[i]);
				data[i].addPropertyChangeListener(new SubtitleMappingListener(i));
			}
		}

		public List<File> getVideoFiles() {
			return new AbstractList<File>() {

				@Override
				public File get(int index) {
					return data[index].getVideoFile();
				}

				@Override
				public int size() {
					return data.length;
				}
			};
		}

		@Override
		public Iterator<SubtitleMapping> iterator() {
			return Arrays.asList(data).iterator();
		}

		public void setOptionColumnVisible(boolean optionColumnVisible) {
			if (this.optionColumnVisible == optionColumnVisible)
				return;

			this.optionColumnVisible = optionColumnVisible;

			// update columns
			fireTableStructureChanged();
		}

		@Override
		public int getColumnCount() {
			return optionColumnVisible ? 2 : 1;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Video";
			case 1:
				return "Subtitle";
			}

			return null;
		}

		@Override
		public int getRowCount() {
			return data.length;
		}

		@Override
		public Object getValueAt(int row, int column) {
			switch (column) {
			case 0:
				return data[row].getVideoFile().getName();
			case 1:
				return data[row];
			}

			return null;
		}

		@Override
		public void setValueAt(Object value, int row, int column) {
			data[row].setSelectedOption((SubtitleDescriptorBean) value);
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return column == 1 && data[row].isEditable();
		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return String.class;
			case 1:
				return SubtitleMapping.class;
			}

			return null;
		}

		private class SubtitleMappingListener implements PropertyChangeListener {

			private final int index;

			public SubtitleMappingListener(int index) {
				this.index = index;
			}

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				// update state and subtitle options
				fireTableRowsUpdated(index, index);
			}
		}
	}

	private static class SubtitleMapping extends AbstractBean {

		private File videoFile;
		private File subtitleFile;

		private SubtitleDescriptorBean selectedOption;
		private List<SubtitleDescriptorBean> options = new ArrayList<SubtitleDescriptorBean>();

		public SubtitleMapping(File videoFile) {
			this.videoFile = videoFile;
		}

		public File getVideoFile() {
			return videoFile;
		}

		public File getSubtitleFile() {
			return subtitleFile;
		}

		public void setSubtitleFile(File subtitleFile) {
			this.subtitleFile = subtitleFile;
			firePropertyChange("subtitleFile", null, this.subtitleFile);
		}

		public boolean isEditable() {
			return subtitleFile == null && options.size() > 0 && (selectedOption == null || (selectedOption.getState() == null || selectedOption.getError() != null));
		}

		public SubtitleDescriptorBean getSelectedOption() {
			return selectedOption;
		}

		public void setSelectedOption(SubtitleDescriptorBean selectedOption) {
			if (this.selectedOption != null) {
				this.selectedOption.removePropertyChangeListener(selectedOptionListener);
			}

			this.selectedOption = selectedOption;
			if (this.selectedOption != null) {
				this.selectedOption.addPropertyChangeListener(selectedOptionListener);
			}
			firePropertyChange("selectedOption", null, this.selectedOption);
		}

		public SubtitleDescriptorBean[] getOptions() {
			return options.toArray(new SubtitleDescriptorBean[0]);
		}

		public void addOptions(List<SubtitleDescriptorBean> options) {
			this.options.addAll(options);

			if (selectedOption == null && options.size() > 0) {
				setSelectedOption(options.get(0));
			}
		}

		private final PropertyChangeListener selectedOptionListener = new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				firePropertyChange("selectedOption", null, selectedOption);
			}
		};
	}

	private static class SubtitleDescriptorBean extends AbstractBean {

		private final File videoFile;
		private final SubtitleDescriptor descriptor;
		private final SubtitleServiceBean service;

		private StateValue state;
		private Exception error;

		public SubtitleDescriptorBean(File videoFile, SubtitleDescriptor descriptor, SubtitleServiceBean service) {
			this.videoFile = videoFile;
			this.descriptor = descriptor;
			this.service = service;
		}

		public SubtitleDescriptor getDescriptor() {
			return descriptor;
		}

		public float getMatchProbability() {
			return service.getMatchProbabilty(videoFile, descriptor);
		}

		public String getText() {
			return formatSubtitle(descriptor.getName(), getLanguageName(), getType());
		}

		public Icon getIcon() {
			return service.getIcon();
		}

		public String getLanguageName() {
			return descriptor.getLanguageName();
		}

		public String getType() {
			return descriptor.getType();
		}

		public MemoryFile fetch() throws Exception {
			setState(StateValue.STARTED);

			try {
				return fetchSubtitle(descriptor);
			} catch (Exception e) {
				// remember and rethrow exception
				throw (error = e);
			} finally {
				setState(StateValue.DONE);
			}
		}

		public Exception getError() {
			return error;
		}

		public StateValue getState() {
			return state;
		}

		public void setState(StateValue state) {
			this.state = state;
			firePropertyChange("state", null, this.state);
		}

		@Override
		public String toString() {
			return getText();
		}
	}

	private static class QueryTask extends SwingWorker<Collection<File>, Map<File, List<SubtitleDescriptorBean>>> {

		private final Component parent;
		private final Collection<SubtitleServiceBean> services;

		private final Collection<File> remainingVideos;
		private final Locale locale;

		public QueryTask(Collection<SubtitleServiceBean> services, Collection<File> videoFiles, Locale locale, Component parent) {
			this.parent = parent;
			this.services = services;
			this.remainingVideos = new TreeSet<File>(videoFiles);
			this.locale = locale;
		}

		@Override
		protected Collection<File> doInBackground() throws Exception {
			for (SubtitleServiceBean service : services) {
				if (isCancelled() || Thread.interrupted()) {
					throw new CancellationException();
				}

				if (remainingVideos.isEmpty()) {
					break;
				}

				try {
					Map<File, List<SubtitleDescriptorBean>> subtitleSet = new HashMap<File, List<SubtitleDescriptorBean>>();
					for (final Entry<File, List<SubtitleDescriptor>> result : service.lookupSubtitles(remainingVideos, locale, parent).entrySet()) {
						Set<SubtitleDescriptor> subtitlesByRelevance = new LinkedHashSet<SubtitleDescriptor>();

						// guess best hash match (default order is open bad due to invalid hash links)
						SubtitleDescriptor bestMatch = getBestMatch(result.getKey(), result.getValue(), false);
						if (bestMatch != null) {
							subtitlesByRelevance.add(bestMatch);
						}

						subtitlesByRelevance.addAll(result.getValue());

						// associate subtitles with services
						List<SubtitleDescriptorBean> subtitles = new ArrayList<SubtitleDescriptorBean>();
						for (SubtitleDescriptor it : subtitlesByRelevance) {
							subtitles.add(new SubtitleDescriptorBean(result.getKey(), it, service));
						}
						subtitleSet.put(result.getKey(), subtitles);
					}

					// only lookup subtitles for remaining videos
					for (Entry<File, List<SubtitleDescriptorBean>> it : subtitleSet.entrySet()) {
						if (it.getValue() != null && it.getValue().size() > 0) {
							remainingVideos.remove(it.getKey());
						}
					}

					publish(subtitleSet);
				} catch (CancellationException e) {
					// don't ignore cancellation
					throw e;
				} catch (InterruptedException e) {
					// don't ignore cancellation
					throw e;
				} catch (Exception e) {
					// log and ignore
					debug.log(Level.WARNING, e.getMessage(), e);
				}
			}

			return remainingVideos;
		}
	}

	private static class DownloadTask extends SwingWorker<File, Void> {

		private final File video;
		private final SubtitleDescriptorBean descriptor;
		private final SubtitleNaming naming;

		public DownloadTask(File video, SubtitleDescriptorBean descriptor, SubtitleNaming naming) {
			this.video = video;
			this.descriptor = descriptor;
			this.naming = naming;
		}

		public SubtitleDescriptorBean getSubtitleBean() {
			return descriptor;
		}

		public File getDestination(MemoryFile subtitle) {
			if (descriptor.getType() == null && subtitle == null)
				return null;

			// prefer type from descriptor because we need to know before we download the actual subtitle file
			String name = naming.format(video, descriptor.getDescriptor(), descriptor.getType());
			return new File(video.getParentFile(), name);
		}

		@Override
		protected File doInBackground() {
			try {
				// fetch subtitle
				MemoryFile subtitle = descriptor.fetch();

				if (isCancelled())
					return null;

				// save to file
				File destination = getDestination(subtitle);
				writeFile(subtitle.getData(), destination);

				return destination;
			} catch (Exception e) {
				// display error message in GUI
				descriptor.error = e;

				// print to error log
				debug.log(Level.WARNING, e.getMessage(), e);
			}

			return null;
		}
	}

	protected static abstract class SubtitleServiceBean extends AbstractBean {

		private final String name;
		private final Icon icon;
		private final URI link;

		private StateValue state = StateValue.PENDING;
		private Exception error = null;

		public SubtitleServiceBean(String name, Icon icon, URI link) {
			this.name = name;
			this.icon = icon;
			this.link = link;
		}

		public String getName() {
			return name;
		}

		public Icon getIcon() {
			return icon;
		}

		public URI getLink() {
			return link;
		}

		public abstract String getDescription();

		public abstract float getMatchProbabilty(File videoFile, SubtitleDescriptor descriptor);

		protected abstract Map<File, List<SubtitleDescriptor>> getSubtitleList(Collection<File> files, Locale locale, Component parent) throws Exception;

		public final Map<File, List<SubtitleDescriptor>> lookupSubtitles(Collection<File> files, Locale locale, Component parent) throws Exception {
			setState(StateValue.STARTED);

			try {
				return getSubtitleList(files, locale, parent);
			} catch (Exception e) {
				throw (error = e);
			} finally {
				setState(StateValue.DONE);
			}
		}

		private void setState(StateValue state) {
			this.state = state;
			firePropertyChange("state", null, this.state);
		}

		public StateValue getState() {
			return state;
		}

		public Throwable getError() {
			return error;
		}
	}

	protected static class VideoHashSubtitleServiceBean extends SubtitleServiceBean {

		private VideoHashSubtitleService service;

		public VideoHashSubtitleServiceBean(VideoHashSubtitleService service) {
			super(service.getName(), service.getIcon(), service.getLink());
			this.service = service;
		}

		@Override
		public String getDescription() {
			return service == WebServices.OpenSubtitles ? "Exact Search" : service.getName();
		}

		@Override
		protected Map<File, List<SubtitleDescriptor>> getSubtitleList(Collection<File> files, Locale locale, Component parent) throws Exception {
			return lookupSubtitlesByHash(service, files, locale, true, false);
		}

		@Override
		public float getMatchProbabilty(File videoFile, SubtitleDescriptor descriptor) {
			return 1;
		}
	}

	protected static class SubtitleProviderBean extends SubtitleServiceBean {

		private SubtitleProvider service;

		public SubtitleProviderBean(SubtitleProvider service, SubtitleAutoMatchDialog inputProvider) {
			super(service.getName(), service.getIcon(), service.getLink());
			this.service = service;
		}

		@Override
		public String getDescription() {
			return "Fuzzy Search";
		}

		@Override
		protected Map<File, List<SubtitleDescriptor>> getSubtitleList(Collection<File> fileSet, Locale locale, Component parent) throws Exception {
			return findSubtitlesByName(service, fileSet, locale, null, true, false);
		}

		@Override
		public float getMatchProbabilty(File videoFile, SubtitleDescriptor descriptor) {
			return SubtitleMetrics.verificationMetric().getSimilarity(videoFile, descriptor);
		}
	}

}
