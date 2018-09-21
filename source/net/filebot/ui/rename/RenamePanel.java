package net.filebot.ui.rename;

import static java.awt.event.KeyEvent.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static javax.swing.KeyStroke.*;
import static javax.swing.SwingUtilities.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.util.ExceptionUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.LoadingOverlayPane.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Window;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import com.google.common.eventbus.Subscribe;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.ListSelection;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import net.filebot.ApplicationFolder;
import net.filebot.History;
import net.filebot.HistorySpooler;
import net.filebot.InvalidResponseException;
import net.filebot.Language;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.StandardRenameAction;
import net.filebot.UserFiles;
import net.filebot.WebServices;
import net.filebot.format.ExpressionFileFormat;
import net.filebot.format.MediaBindingBean;
import net.filebot.media.LocalDatasource;
import net.filebot.media.MetaAttributes;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.similarity.Match;
import net.filebot.ui.SelectDialog;
import net.filebot.ui.rename.FormatDialog.Mode;
import net.filebot.ui.rename.RenameModel.FormattedFuture;
import net.filebot.ui.transfer.BackgroundFileTransferablePolicy;
import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.ui.transfer.TransferablePolicy.TransferAction;
import net.filebot.util.AlphanumComparator;
import net.filebot.util.PreferencesMap.PreferencesEntry;
import net.filebot.util.ui.ActionPopup;
import net.filebot.util.ui.DefaultFancyListCellRenderer;
import net.filebot.util.ui.LoadingOverlayPane;
import net.filebot.vfs.FileInfo;
import net.filebot.vfs.SimpleFileInfo;
import net.filebot.web.AudioTrack;
import net.filebot.web.AudioTrackFormat;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeFormat;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.Movie;
import net.filebot.web.MovieFormat;
import net.filebot.web.MovieIdentificationService;
import net.filebot.web.MusicIdentificationService;
import net.filebot.web.SortOrder;
import net.miginfocom.swing.MigLayout;

public class RenamePanel extends JComponent {

	public static final String MATCH_MODE_OPPORTUNISTIC = "Opportunistic";
	public static final String MATCH_MODE_STRICT = "Strict";

	protected final RenameModel renameModel = new RenameModel();

	protected final RenameList<FormattedFuture> namesList = new RenameList<FormattedFuture>(renameModel.names());

	protected final RenameList<File> filesList = new RenameList<File>(renameModel.files());

	protected final MatchAction matchAction = new MatchAction(renameModel);

	protected final RenameAction renameAction = new RenameAction(renameModel);

	private static final PreferencesEntry<String> persistentEpisodeFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.episode");
	private static final PreferencesEntry<String> persistentMovieFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.movie");
	private static final PreferencesEntry<String> persistentMusicFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.music");
	private static final PreferencesEntry<String> persistentFileFormat = Settings.forPackage(RenamePanel.class).entry("rename.format.file");

	private static final PreferencesEntry<String> persistentLastFormatState = Settings.forPackage(RenamePanel.class).entry("rename.last.format.state").defaultValue(Mode.Episode.name());
	private static final PreferencesEntry<String> persistentPreferredMatchMode = Settings.forPackage(RenamePanel.class).entry("rename.match.mode").defaultValue(MATCH_MODE_OPPORTUNISTIC);
	private static final PreferencesEntry<String> persistentPreferredLanguage = Settings.forPackage(RenamePanel.class).entry("rename.language").defaultValue("en");
	private static final PreferencesEntry<String> persistentPreferredEpisodeOrder = Settings.forPackage(RenamePanel.class).entry("rename.episode.order").defaultValue("Airdate");

	private static final Map<String, Preset> persistentPresets = Settings.forPackage(RenamePanel.class).node("presets").asMap(Preset.class);

	public RenamePanel() {
		namesList.setTitle("New Names");
		namesList.setTransferablePolicy(new NamesListTransferablePolicy(renameModel.values()));

		filesList.setTitle("Original Files");
		filesList.setTransferablePolicy(new FilesListTransferablePolicy(renameModel.files()));

		// restore icon indicating current match mode
		matchAction.setMatchMode(isMatchModeStrict());

		try {
			// restore custom episode formatter
			renameModel.useFormatter(Episode.class, new ExpressionFormatter(persistentEpisodeFormat.getValue(), EpisodeFormat.SeasonEpisode, Episode.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom movie formatter
			renameModel.useFormatter(Movie.class, new ExpressionFormatter(persistentMovieFormat.getValue(), MovieFormat.NameYear, Movie.class));
		} catch (Exception e) {
			// use default movie formatter
			renameModel.useFormatter(Movie.class, new MovieFormatter());
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(persistentMusicFormat.getValue(), new AudioTrackFormat(), AudioTrack.class));
		} catch (Exception e) {
			// use default formatter
		}

		try {
			// restore custom music formatter
			renameModel.useFormatter(File.class, new ExpressionFormatter(persistentFileFormat.getValue(), new FileNameFormat(), File.class));
		} catch (Exception e) {
			// make sure to put File formatter at position 3
			renameModel.useFormatter(File.class, new FileNameFormatter());
		} finally {
			// use default filename formatter
			renameModel.useFormatter(FileInfo.class, new FileNameFormatter());
		}

		RenameListCellRenderer cellrenderer = new RenameListCellRenderer(renameModel, ApplicationFolder.UserHome.get());

		namesList.getListComponent().setCellRenderer(cellrenderer);
		filesList.getListComponent().setCellRenderer(cellrenderer);

		DefaultEventSelectionModel<Match<Object, File>> selectionModel = new DefaultEventSelectionModel<Match<Object, File>>(renameModel.matches());
		selectionModel.setSelectionMode(ListSelection.SINGLE_SELECTION);

		// use the same selection model for both lists to synchronize selection
		namesList.getListComponent().setSelectionModel(selectionModel);
		filesList.getListComponent().setSelectionModel(selectionModel);

		// synchronize viewports
		new ScrollPaneSynchronizer(namesList, filesList);

		// delete items from both lists
		Action removeAction = newAction("Exclude Selected Items", ResourceManager.getIcon("dialog.cancel"), evt -> {
			RenameList list = null;
			boolean deleteCell;

			if (evt.getSource() instanceof JButton) {
				list = filesList;
				deleteCell = isShiftOrAltDown(evt);
			} else {
				list = ((RenameList) evt.getSource());
				deleteCell = isShiftOrAltDown(evt);
			}

			int index = list.getListComponent().getSelectedIndex();
			if (index >= 0) {
				if (deleteCell) {
					EventList eventList = list.getModel();
					if (index < eventList.size()) {
						list.getModel().remove(index);
					}
				} else {
					renameModel.matches().remove(index);
				}
				int maxIndex = list.getModel().size() - 1;
				if (index > maxIndex) {
					index = maxIndex;
				}
				if (index >= 0) {
					list.getListComponent().setSelectedIndex(index);
				}
			}
		});
		namesList.setRemoveAction(removeAction);
		filesList.setRemoveAction(removeAction);

		// create Match button
		JButton matchButton = new JButton(matchAction);
		matchButton.setVerticalTextPosition(SwingConstants.BOTTOM);
		matchButton.setHorizontalTextPosition(SwingConstants.CENTER);

		// create Rename button
		JButton renameButton = new JButton(renameAction);
		renameButton.setVerticalTextPosition(SwingConstants.BOTTOM);
		renameButton.setHorizontalTextPosition(SwingConstants.CENTER);

		// create fetch popup
		ActionPopup fetchPopup = createFetchPopup();

		Action fetchPopupAction = new ShowPopupAction("Fetch Data", ResourceManager.getIcon("action.fetch"));
		JButton fetchButton = new JButton(fetchPopupAction);
		filesList.getListComponent().setComponentPopupMenu(fetchPopup);
		namesList.getListComponent().setComponentPopupMenu(fetchPopup);
		fetchButton.setComponentPopupMenu(fetchPopup);
		matchButton.setComponentPopupMenu(fetchPopup);
		namesList.getButtonPanel().add(fetchButton, "gap 0, sgy button");

		namesList.getListComponent().setComponentPopupMenu(fetchPopup);
		fetchButton.setComponentPopupMenu(fetchPopup);

		// settings popup and button
		ActionPopup settingsPopup = createSettingsPopup();
		Action settingsPopupAction = new ShowPopupAction("Settings", ResourceManager.getIcon("action.settings"));
		JButton settingsButton = createImageButton(settingsPopupAction);
		settingsButton.setComponentPopupMenu(settingsPopup);
		renameButton.setComponentPopupMenu(settingsPopup);
		namesList.getButtonPanel().add(settingsButton, "gap indent, sgy button");

		// open rename log button
		filesList.getButtonPanel().add(createImageButton(removeAction), "gap 0, sgy button", 2);
		filesList.getButtonPanel().add(createImageButton(clearFilesAction), "gap 0, sgy button");
		filesList.getButtonPanel().add(createImageButton(openHistoryAction), "gap indent, sgy button");

		// create macros popup
		JButton presetsButton = createImageButton(new ShowPresetsPopupAction());
		filesList.getButtonPanel().add(presetsButton, "gap 0, sgy button");

		// show popup on actionPerformed only when names list is empty
		matchButton.addActionListener(evt -> {
			if (renameModel.names().isEmpty()) {
				fetchPopupAction.actionPerformed(evt);
			}
		});

		// reveal file location on double click
		filesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				getWindow(evt.getSource()).setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				try {
					JList list = (JList) evt.getSource();
					if (list.getSelectedIndex() >= 0) {
						UserFiles.revealFiles(list.getSelectedValuesList());
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e.getMessage(), e);
				} finally {
					getWindow(evt.getSource()).setCursor(Cursor.getDefaultCursor());
				}
			}
		}));

		// reveal file location on double click
		namesList.getListComponent().addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				JList list = (JList) evt.getSource();
				if (list.getSelectedIndex() >= 0) {
					Match<Object, File> match = renameModel.getMatch(list.getSelectedIndex());
					Map<File, Object> context = renameModel.getMatchContext(match);

					if (match.getValue() != null) {
						MediaBindingBean sample = new MediaBindingBean(match.getValue(), match.getCandidate(), context);
						showFormatEditor(sample);
					}
				}
			}
		}));

		setLayout(new MigLayout("fill, insets dialog, gapx 10px", "[fill][align center, pref!][fill]", "align 33%"));
		add(new LoadingOverlayPane(filesList, filesList, "37px", "30px"), "grow, sizegroupx list");

		BackgroundFileTransferablePolicy<?> transferablePolicy = (BackgroundFileTransferablePolicy<?>) filesList.getTransferablePolicy();
		transferablePolicy.addPropertyChangeListener(evt -> {
			if (BackgroundFileTransferablePolicy.LOADING_PROPERTY.equals(evt.getPropertyName())) {
				filesList.firePropertyChange(LoadingOverlayPane.LOADING_PROPERTY, (boolean) evt.getOldValue(), (boolean) evt.getNewValue());
			}
		});

		// make buttons larger
		matchButton.setMargin(new Insets(3, 14, 2, 14));
		renameButton.setMargin(new Insets(6, 11, 2, 11));

		add(matchButton, "split 2, flowy, sizegroupx button");
		add(renameButton, "gapy 30px, sizegroupx button");

		add(new LoadingOverlayPane(namesList, namesList, "37px", "30px"), "grow, sizegroupx list");

		// install F2 and 1..9 keystroke actions
		SwingUtilities.invokeLater(this::installKeyStrokeActions);
	}

	private void installKeyStrokeActions() {
		// manual force name via F2
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F2, 0), newAction("Force Name", evt -> {
			withWaitCursor(evt.getSource(), () -> {
				if (namesList.getModel().isEmpty()) {
					// match to xattr metadata object or the file itself
					Map<File, Object> xattr = LocalDatasource.XATTR.match(renameModel.files(), false);

					renameModel.clear();
					renameModel.addAll(xattr.values(), xattr.keySet());
				} else {
					int index = namesList.getListComponent().getSelectedIndex();
					if (index >= 0) {
						File file = (File) filesList.getListComponent().getModel().getElementAt(index);
						Object object = namesList.getListComponent().getModel().getElementAt(index);

						String string = showInputDialog("Enter Name:", object.toString(), "Enter Name", RenamePanel.this);
						if (string != null && string.length() > 0) {
							renameModel.matches().set(index, new Match<Object, File>(string + '.' + getExtension(file), file));
						}
					}
				}
			});
		}));

		// map 1..9 number keys to presets
		for (int presetNumber = 1; presetNumber <= 9; presetNumber++) {
			int index = presetNumber - 1;

			installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(Character.forDigit(presetNumber, 10), 0), newAction("Preset " + presetNumber, evt -> {
				try {
					List<Preset> presets = getPresets();

					if (index < presets.size()) {
						new ApplyPresetAction(presets.get(index)).actionPerformed(evt);
					} else {
						new ShowPresetsPopupAction().actionPerformed(evt);
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::getMessage);
				}
			}));
		}

		// copy debug information (paths and objects)
		installAction(this, WHEN_IN_FOCUSED_WINDOW, getKeyStroke(VK_F7, 0), newAction("Copy Debug Information", evt -> {
			withWaitCursor(evt.getSource(), () -> {
				String text = getDebugInfo();
				if (text.length() > 0) {
					copyToClipboard(text);
					log.info("Match model has been copied to clipboard");
				} else {
					log.warning("Match model is empty");
				}
			});
		}));
	}

	private boolean isMatchModeStrict() {
		return MATCH_MODE_STRICT.equalsIgnoreCase(persistentPreferredMatchMode.getValue());
	}

	private ActionPopup createPresetsPopup() {
		ActionPopup actionPopup = new ActionPopup("Presets", ResourceManager.getIcon("action.script"));

		List<Preset> presets = getPresets();
		if (presets.size() > 0) {
			for (Preset preset : presets) {
				actionPopup.add(new ApplyPresetAction(preset));
			}
			actionPopup.addSeparator();
		}

		actionPopup.add(newAction("Edit Presets", ResourceManager.getIcon("script.add"), evt -> {
			Window window = getWindow(evt.getSource());

			Action newPreset = newAction("New Preset …", ResourceManager.getIcon("script.add"), a -> {
				Optional.ofNullable(JOptionPane.showInputDialog(window, "Preset Name:", a.getActionCommand(), JOptionPane.PLAIN_MESSAGE, null, null, "My Preset")).map(Object::toString).map(String::trim).filter(s -> s.length() > 0).ifPresent(n -> {
					showPresetEditor(new Preset(n, null, null, null, null, null, null, null, null), window);
				});
			});

			List<Object> options = new ArrayList<Object>(presets);
			options.add(newPreset);

			SelectDialog<Object> selectDialog = new SelectDialog<Object>(window, options) {

				@Override
				protected void configureValue(DefaultFancyListCellRenderer render, Object value) {
					if (value instanceof Preset) {
						Preset preset = (Preset) value;
						render.setIcon(preset.getIcon());
					} else if (value instanceof Action) {
						Action action = (Action) value;
						render.setText((String) action.getValue(Action.NAME));
						render.setIcon((Icon) action.getValue(Action.SMALL_ICON));
					}
				}
			};

			selectDialog.setTitle("Edit Presets");
			selectDialog.setLocation(getOffsetLocation(selectDialog.getOwner()));
			selectDialog.setMinimumSize(new Dimension(250, 250));
			selectDialog.pack();
			selectDialog.setVisible(true);

			Object selection = selectDialog.getSelectedValue();
			if (selection instanceof Preset) {
				Preset preset = (Preset) selection;
				showPresetEditor(preset, window);
			} else if (selection instanceof Action) {
				Action action = (Action) selection;
				action.actionPerformed(new ActionEvent(newPreset, ActionEvent.ACTION_PERFORMED, "New Preset …"));
			}
		}));

		return actionPopup;
	}

	private ActionPopup createFetchPopup() {
		ActionPopup actionPopup = new ActionPopup("Fetch & Match Data", ResourceManager.getIcon("action.fetch"));

		actionPopup.addDescription("Episode Mode:");

		// create actions for match popup episode list completion
		for (EpisodeListProvider db : WebServices.getEpisodeListProviders()) {
			actionPopup.add(new AutoCompleteAction(db.getName(), db.getIcon(), () -> new EpisodeListMatcher(db, db == WebServices.AniDB)));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Movie Mode:");

		// create action for movie name completion
		for (MovieIdentificationService it : WebServices.getMovieIdentificationServices()) {
			actionPopup.add(new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MovieMatcher(it)));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Music Mode:");
		for (MusicIdentificationService it : WebServices.getMusicIdentificationServices()) {
			actionPopup.add(new AutoCompleteAction(it.getName(), it.getIcon(), () -> new MusicMatcher(it)));
		}

		actionPopup.addSeparator();
		actionPopup.addDescription("Smart Mode:");
		actionPopup.add(new AutoCompleteAction("Autodetect", ResourceManager.getIcon("action.auto"), AutoDetectMatcher::new));

		actionPopup.addSeparator();
		actionPopup.addDescription("Options:");

		actionPopup.add(newAction("Edit Format", ResourceManager.getIcon("action.format"), evt -> showFormatEditor(null)));

		actionPopup.add(newAction("Preferences", ResourceManager.getIcon("action.preferences"), evt -> {
			String[] modes = new String[] { MATCH_MODE_OPPORTUNISTIC, MATCH_MODE_STRICT };
			JComboBox modeCombo = new JComboBox(modes);

			List<Language> languages = new ArrayList<Language>();
			languages.addAll(Language.preferredLanguages()); // add preferred languages first
			languages.addAll(Language.availableLanguages()); // then others

			JComboBox orderCombo = new JComboBox(SortOrder.values());
			JList languageList = new JList(languages.toArray());
			languageList.setCellRenderer(new DefaultListCellRenderer() {

				@Override
				public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
					super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
					if (value != null) {
						setText(((Language) value).getName());
						setIcon(ResourceManager.getFlagIcon(((Language) value).getCode()));
					}
					return this;
				}
			});

			// restore current preference values
			try {
				modeCombo.setSelectedItem(persistentPreferredMatchMode.getValue());
				orderCombo.setSelectedItem(SortOrder.forName(persistentPreferredEpisodeOrder.getValue()));

				String selectedLanguage = persistentPreferredLanguage.getValue();
				languages.stream().filter(l -> l.getCode().equals(selectedLanguage)).findFirst().ifPresent(l -> languageList.setSelectedValue(l, true));
			} catch (Exception e) {
				debug.log(Level.WARNING, e.getMessage(), e);
			}

			JScrollPane spModeCombo = new JScrollPane(modeCombo, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			spModeCombo.setBorder(new CompoundBorder(new TitledBorder("Match Mode"), spModeCombo.getBorder()));
			JScrollPane spLanguageList = new JScrollPane(languageList);
			spLanguageList.setBorder(new CompoundBorder(new TitledBorder("Language"), spLanguageList.getBorder()));
			JScrollPane spOrderCombo = new JScrollPane(orderCombo, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			spOrderCombo.setBorder(new CompoundBorder(new TitledBorder("Episode Order"), spOrderCombo.getBorder()));

			// fix background issues on OSX
			spModeCombo.setOpaque(false);
			spLanguageList.setOpaque(false);
			spOrderCombo.setOpaque(false);

			JPanel message = new JPanel(new MigLayout("fill, flowy, insets 0"));
			message.add(spModeCombo, "grow, hmin 24px");
			message.add(spLanguageList, "grow, hmin 50px");
			message.add(spOrderCombo, "grow, hmin 24px");
			JOptionPane pane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			pane.createDialog(getWindowAncestor(RenamePanel.this), "Preferences").setVisible(true);

			if (pane.getValue() != null && pane.getValue().equals(JOptionPane.OK_OPTION)) {
				persistentPreferredMatchMode.setValue((String) modeCombo.getSelectedItem());
				persistentPreferredLanguage.setValue(((Language) languageList.getSelectedValue()).getCode());
				persistentPreferredEpisodeOrder.setValue(((SortOrder) orderCombo.getSelectedItem()).name());

				// update UI
				matchAction.setMatchMode(isMatchModeStrict());
			}
		}));

		return actionPopup;
	}

	private ActionPopup createSettingsPopup() {
		ActionPopup actionPopup = new ActionPopup("Rename Options", ResourceManager.getIcon("action.settings"));

		actionPopup.addDescription("Extension:");
		actionPopup.add(new SetRenameMode(false, "Preserve", ResourceManager.getIcon("action.extension.preserve")));
		actionPopup.add(new SetRenameMode(true, "Override", ResourceManager.getIcon("action.extension.override")));

		actionPopup.addSeparator();

		actionPopup.addDescription("Action:");
		for (StandardRenameAction action : Preset.getSupportedActions()) {
			actionPopup.add(new SetRenameAction(action));
		}

		return actionPopup;
	}

	private Mode getFormatEditorMode(MediaBindingBean binding) {
		if (binding != null) {
			if (binding.getInfoObject() instanceof Episode) {
				return Mode.Episode;
			} else if (binding.getInfoObject() instanceof Movie) {
				return Mode.Movie;
			} else if (binding.getInfoObject() instanceof AudioTrack) {
				return Mode.Music;
			} else if (binding.getInfoObject() instanceof File) {
				return Mode.File;
			} else {
				throw new IllegalArgumentException("Cannot format class: " + binding.getInfoObjectType()); // ignore objects that cannot be formatted
			}
		}

		try {
			return Mode.valueOf(persistentLastFormatState.getValue()); // restore previous mode
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::getMessage);
		}

		return Mode.Episode; // default to Episode mode
	}

	private void showFormatEditor(MediaBindingBean binding) {
		withWaitCursor(this, () -> {
			FormatDialog dialog = new FormatDialog(getWindowAncestor(RenamePanel.this), getFormatEditorMode(binding), binding, binding != null);
			dialog.setLocation(getOffsetLocation(dialog.getOwner()));
			dialog.setVisible(true);

			if (dialog.submit()) {
				switch (dialog.getMode()) {
				case Episode:
					renameModel.useFormatter(Episode.class, new ExpressionFormatter(dialog.getFormat().getExpression(), EpisodeFormat.SeasonEpisode, Episode.class));
					persistentEpisodeFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Movie:
					renameModel.useFormatter(Movie.class, new ExpressionFormatter(dialog.getFormat().getExpression(), MovieFormat.NameYear, Movie.class));
					persistentMovieFormat.setValue(dialog.getFormat().getExpression());
					break;
				case Music:
					renameModel.useFormatter(AudioTrack.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new AudioTrackFormat(), AudioTrack.class));
					persistentMusicFormat.setValue(dialog.getFormat().getExpression());
					break;
				case File:
					renameModel.useFormatter(File.class, new ExpressionFormatter(dialog.getFormat().getExpression(), new FileNameFormat(), File.class));
					persistentFileFormat.setValue(dialog.getFormat().getExpression());
					break;
				}

				if (binding == null) {
					persistentLastFormatState.setValue(dialog.getMode().name());
				}
			}
		});
	}

	private void showPresetEditor(Preset preset, Window owner) {
		try {
			PresetEditor presetEditor = new PresetEditor(owner);
			presetEditor.setPreset(preset);

			presetEditor.setLocation(getOffsetLocation(presetEditor.getOwner()));
			presetEditor.setVisible(true);

			switch (presetEditor.getResult()) {
			case SET:
				persistentPresets.put(preset.getName(), presetEditor.getPreset());
				break;
			case DELETE:
				persistentPresets.remove(preset.getName());
				break;
			case CANCEL:
				break;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
	}

	private List<Preset> getPresets() {
		// load Presets and ensure Preset order on all platforms (e.g. Windows Registry Preferences are sorted alphabetically, but the same is not guaranteed for other platforms)
		List<Preset> presets = new ArrayList<Preset>(persistentPresets.values());
		presets.sort(comparing(Preset::getName, new AlphanumComparator(Locale.getDefault())));
		return presets;
	}

	private String getDebugInfo() throws Exception {
		StringBuilder sb = new StringBuilder();

		for (Match<Object, File> m : renameModel.matches()) {
			String f = getStructurePathTail(m.getCandidate()).getPath();
			Object v = m.getValue();

			// convert FastFile items
			if (v instanceof File) {
				v = new SimpleFileInfo(getStructurePathTail((File) v).getPath(), ((File) v).length());
			}

			sb.append(f).append('\t').append(MetaAttributes.toJson(v)).append('\n');
		}

		return sb.toString();
	}

	private final Action clearFilesAction = newAction("Clear All", ResourceManager.getIcon("action.clear"), evt -> {
		if (isShiftOrAltDown(evt)) {
			renameModel.files().clear();
		} else {
			renameModel.clear();
		}
	});

	private final Action openHistoryAction = newAction("Open History", ResourceManager.getIcon("action.report"), evt -> {
		try {
			History model = HistorySpooler.getInstance().getCompleteHistory();

			HistoryDialog dialog = new HistoryDialog(getWindow(RenamePanel.this));
			dialog.setLocationRelativeTo(RenamePanel.this);
			dialog.setModel(model);

			// show and block
			dialog.setVisible(true);
		} catch (Exception e) {
			log.log(Level.WARNING, e, cause(getRootCause(e)));
		}
	});

	@Subscribe
	public void handle(Transferable transferable) throws Exception {
		for (TransferablePolicy handler : new TransferablePolicy[] { filesList.getTransferablePolicy(), namesList.getTransferablePolicy() }) {
			if (handler != null && handler.accept(transferable)) {
				handler.handleTransferable(transferable, TransferAction.PUT);
				return;
			}
		}
	}

	private static class ShowPopupAction extends AbstractAction {

		public ShowPopupAction(String name, Icon icon) {
			super(name, icon);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JComponent source = (JComponent) e.getSource();
			source.getComponentPopupMenu().show(source, -3, source.getHeight() + 4);
		}
	};

	private class ShowPresetsPopupAction extends AbstractAction {

		public ShowPresetsPopupAction() {
			super("Presets", ResourceManager.getIcon("action.script"));
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			try {
				JComponent source = (JComponent) evt.getSource();
				createPresetsPopup().show(source, -3, source.getHeight() + 4);
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
			}
		}
	};

	private class ApplyPresetAction extends AutoCompleteAction {

		private Preset preset;

		public ApplyPresetAction(Preset preset) {
			super(preset.getName(), preset.getIcon(), preset::getAutoCompleteMatcher);
			this.preset = preset;
		}

		@Override
		public List<File> getFiles(ActionEvent evt) {
			File inputFolder = preset.getInputFolder();

			if (inputFolder == null) {
				return super.getFiles(evt); // default behaviour
			}

			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(getWindow(RenamePanel.this), singleton(inputFolder))) {
					return emptyList();
				}
			}

			try {
				List<File> selection = onSecondaryLoop(preset::selectFiles); // run potentially long-running operations on secondary EDT

				if (selection.size() > 0) {
					renameModel.clear();
					renameModel.files().addAll(selection);
					return selection;
				}

				log.info("No files have been selected.");
			} catch (Exception e) {
				log.log(Level.WARNING, e, e::toString);
			}

			return null; // cancel operation
		}

		@Override
		public boolean isStrict(ActionEvent evt) {
			return preset.getMatchMode() != null ? MATCH_MODE_STRICT.equals(preset.getMatchMode()) : super.isStrict(evt);
		}

		@Override
		public SortOrder getSortOrder(ActionEvent evt) {
			return preset.getSortOrder() != null ? preset.getSortOrder() : super.getSortOrder(evt);
		}

		@Override
		public Locale getLocale(ActionEvent evt) {
			return preset.getLanguage() != null ? preset.getLanguage().getLocale() : super.getLocale(evt);
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			SwingWorker<ExpressionFormatter, Void> worker = newSwingWorker(() -> {
				ExpressionFileFormat format = preset.getFormat();

				if (format != null && preset.getDatasource() != null) {
					switch (Mode.getMode(preset.getDatasource())) {
					case Episode:
						return new ExpressionFormatter(format, EpisodeFormat.SeasonEpisode, Episode.class);
					case Movie:
						return new ExpressionFormatter(format, MovieFormat.NameYear, Movie.class);
					case Music:
						return new ExpressionFormatter(format, new AudioTrackFormat(), AudioTrack.class);
					case File:
						return new ExpressionFormatter(format, new FileNameFormat(), File.class);
					}
				}

				return null;
			}, formatter -> {
				if (formatter != null) {
					renameModel.useFormatter(formatter.getTargetClass(), formatter);
				}

				if (preset.getRenameAction() != null) {
					new SetRenameAction(preset.getRenameAction()).actionPerformed(evt);
				}

				super.actionPerformed(evt);
			});

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			worker.execute();
		}
	}

	private class SetRenameMode extends AbstractAction {

		private final boolean activate;

		private SetRenameMode(boolean activate, String name, Icon icon) {
			super(name, icon);
			this.activate = activate;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			renameModel.setPreserveExtension(!activate);

			// display changed state
			filesList.repaint();
		}
	}

	private class SetRenameAction extends AbstractAction {

		private final StandardRenameAction action;

		public SetRenameAction(StandardRenameAction action) {
			super(action.getDisplayName(), ResourceManager.getIcon("rename.action." + action.name().toLowerCase()));
			this.action = action;
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			if (action == StandardRenameAction.MOVE) {
				renameAction.resetValues();
			} else {
				renameAction.putValue(RenameAction.RENAME_ACTION, action);
				renameAction.putValue(NAME, this.getValue(NAME));
				renameAction.putValue(SMALL_ICON, ResourceManager.getIcon("action." + action.name().toLowerCase()));
			}
		}
	}

	private class AutoCompleteAction extends AbstractAction {

		protected final Supplier<AutoCompleteMatcher> matcher;

		public AutoCompleteAction(String name, Icon icon, Supplier<AutoCompleteMatcher> matcher) {
			super(name, icon);

			// create matcher when required
			this.matcher = matcher;

			// disable action while episode list matcher is working
			namesList.addPropertyChangeListener(LOADING_PROPERTY, evt -> {
				// disable action while loading is in progress
				setEnabled(!(Boolean) evt.getNewValue());
			});
		}

		public List<File> getFiles(ActionEvent evt) {
			return renameModel.files();
		}

		public boolean isStrict(ActionEvent evt) {
			return isMatchModeStrict();
		}

		public SortOrder getSortOrder(ActionEvent evt) {
			return SortOrder.forName(persistentPreferredEpisodeOrder.getValue());
		}

		public Locale getLocale(ActionEvent evt) {
			return Language.getLanguage(persistentPreferredLanguage.getValue()).getLocale();
		}

		private boolean isAutoDetectionEnabled(ActionEvent evt) {
			return !isShiftOrAltDown(evt); // skip name auto-detection if SHIFT is pressed
		}

		@Override
		public void actionPerformed(ActionEvent evt) {
			// clear names list
			renameModel.values().clear();

			// select files
			List<File> files = getFiles(evt);
			if (files == null) {
				namesList.firePropertyChange(LOADING_PROPERTY, true, false);
				return;
			}

			List<File> remainingFiles = new LinkedList<File>(files);
			boolean strict = isStrict(evt);
			SortOrder order = getSortOrder(evt);
			Locale locale = getLocale(evt);
			boolean autodetection = isAutoDetectionEnabled(evt);

			if (isMacSandbox()) {
				if (!MacAppUtilities.askUnlockFolders(getWindow(RenamePanel.this), remainingFiles)) {
					namesList.firePropertyChange(LOADING_PROPERTY, true, false);
					return;
				}
			}

			SwingWorker<List<Match<File, ?>>, Void> worker = new SwingWorker<List<Match<File, ?>>, Void>() {

				@Override
				protected List<Match<File, ?>> doInBackground() throws Exception {
					List<Match<File, ?>> matches = matcher.get().match(remainingFiles, strict, order, locale, autodetection, getWindow(RenamePanel.this));

					// remove matched files
					for (Match<File, ?> match : matches) {
						remainingFiles.remove(match.getValue());
					}

					return matches;
				}

				@Override
				protected void done() {
					try {
						List<Match<Object, File>> matches = new ArrayList<Match<Object, File>>();

						for (Match<File, ?> match : get()) {
							matches.add(new Match<Object, File>(match.getCandidate(), match.getValue()));
						}

						renameModel.clear();
						renameModel.addAll(matches);

						// add remaining file entries
						renameModel.files().addAll(remainingFiles);
					} catch (Exception e) {
						// ignore cancellation exception
						if (findCause(e, CancellationException.class) != null) {
							return;
						}

						// common error message
						if (findCause(e, InvalidResponseException.class) != null) {
							log.log(Level.WARNING, findCause(e, InvalidResponseException.class).getMessage());
							return;
						}

						// generic error message
						log.log(Level.WARNING, e, cause(getRootCause(e)));
					} finally {
						// auto-match finished
						namesList.firePropertyChange(LOADING_PROPERTY, true, false);
					}
				}
			};

			// auto-match in progress
			namesList.firePropertyChange(LOADING_PROPERTY, false, true);
			worker.execute();
		}

	}

}
