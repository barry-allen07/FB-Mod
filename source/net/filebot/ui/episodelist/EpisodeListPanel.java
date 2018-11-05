package net.filebot.ui.episodelist;

import static net.filebot.Settings.*;
import static net.filebot.ui.episodelist.SeasonSpinnerModel.*;
import static net.filebot.util.ui.SwingUI.*;
import static net.filebot.web.EpisodeUtilities.*;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;

import net.filebot.Language;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.WebServices;
import net.filebot.media.MediaDetection;
import net.filebot.similarity.Normalization;
import net.filebot.ui.AbstractSearchPanel;
import net.filebot.ui.FileBotList;
import net.filebot.ui.LanguageComboBox;
import net.filebot.ui.PanelBuilder;
import net.filebot.ui.SelectDialog;
import net.filebot.ui.transfer.SaveAction;
import net.filebot.util.ui.LabelProvider;
import net.filebot.util.ui.SelectButton;
import net.filebot.util.ui.SimpleLabelProvider;
import net.filebot.util.ui.SwingEventBus;
import net.filebot.web.Episode;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.SearchResult;
import net.filebot.web.SeasonOutOfBoundsException;
import net.filebot.web.SortOrder;

public class EpisodeListPanel extends AbstractSearchPanel<EpisodeListProvider, Episode> {

	private SeasonSpinnerModel seasonSpinnerModel = new SeasonSpinnerModel();
	private LanguageComboBox languageComboBox = new LanguageComboBox(Language.getLanguage("en"), getSettings());
	private JComboBox sortOrderComboBox = new JComboBox(SortOrder.values());

	public EpisodeListPanel() {
		historyPanel.setColumnHeader(0, "TV Series");
		historyPanel.setColumnHeader(1, "Number of Episodes");

		JSpinner seasonSpinner = new JSpinner(seasonSpinnerModel);
		seasonSpinner.setEditor(new SeasonSpinnerEditor(seasonSpinner));

		// set minimum size to "All Seasons" preferred size
		Dimension d = seasonSpinner.getPreferredSize();
		d.width += 12;
		seasonSpinner.setMinimumSize(d);

		// add after text field
		add(seasonSpinner, "sgy button, gap indent", 1);
		add(sortOrderComboBox, "sgy button, gap rel", 2);
		add(languageComboBox, "sgy button, gap indent+5", 3);

		searchTextField.getSelectButton().addPropertyChangeListener(SelectButton.SELECTED_VALUE, selectButtonListener);

		installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), new SpinSeasonAction(1));
		installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), new SpinSeasonAction(-1));
	}

	@Override
	protected Collection<String> getHistory(EpisodeListProvider engine) throws Exception {
		final List<String> names = new ArrayList<String>(100000);
		final SearchResult[] index = (engine == WebServices.AniDB) ? MediaDetection.releaseInfo.getAnidbIndex() : MediaDetection.releaseInfo.getTheTVDBIndex();
		for (SearchResult it : index) {
			for (String n : it.getEffectiveNames()) {
				names.add(Normalization.removeTrailingBrackets(n));
			}
		}
		return names;
	}

	@Override
	protected EpisodeListProvider[] getSearchEngines() {
		return WebServices.getEpisodeListProviders();
	}

	@Override
	protected LabelProvider<EpisodeListProvider> getSearchEngineLabelProvider() {
		return SimpleLabelProvider.forClass(EpisodeListProvider.class);
	}

	@Override
	protected Settings getSettings() {
		return Settings.forPackage(EpisodeListPanel.class);
	}

	@Override
	protected EpisodeListRequestProcessor createRequestProcessor() {
		EpisodeListProvider provider = searchTextField.getSelectButton().getSelectedValue();
		String text = searchTextField.getText().trim();
		int season = seasonSpinnerModel.getSeason();
		SortOrder order = (SortOrder) sortOrderComboBox.getSelectedItem();
		Locale language = languageComboBox.getModel().getSelectedItem().getLocale();

		return new EpisodeListRequestProcessor(new EpisodeListRequest(provider, text, season, order, language));
	};

	private final PropertyChangeListener selectButtonListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			EpisodeListProvider provider = searchTextField.getSelectButton().getSelectedValue();

			// lock season spinner on "All Seasons" if provider doesn't support fetching of single seasons
			if (!provider.hasSeasonSupport()) {
				seasonSpinnerModel.lock(ALL_SEASONS);
			} else {
				seasonSpinnerModel.unlock();
			}
		}
	};

	private class SpinSeasonAction extends AbstractAction {

		public SpinSeasonAction(int spin) {
			super(String.format("Spin%+d", spin));
			putValue("spin", spin);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			seasonSpinnerModel.spin((Integer) getValue("spin"));
		}
	}

	protected static class EpisodeListRequest extends Request {

		public final EpisodeListProvider provider;
		public final int season;
		public final SortOrder order;
		public final Locale language;

		public EpisodeListRequest(EpisodeListProvider provider, String searchText, int season, SortOrder order, Locale language) {
			super(searchText);
			this.provider = provider;
			this.season = season;
			this.order = order;
			this.language = language;
		}
	}

	protected static class EpisodeListRequestProcessor extends RequestProcessor<EpisodeListRequest, Episode> {

		public EpisodeListRequestProcessor(EpisodeListRequest request) {
			super(request, new EpisodeListTab());
		}

		@Override
		public Collection<SearchResult> search() throws Exception {
			return request.provider.search(request.getSearchText(), request.language);
		}

		@Override
		public Collection<Episode> fetch() throws Exception {
			List<Episode> episodes = request.provider.getEpisodeList(getSearchResult(), request.order, request.language);

			if (request.season != ALL_SEASONS) {
				List<Episode> episodeForSeason = filterBySeason(episodes, request.season);
				if (episodeForSeason.isEmpty()) {
					throw new SeasonOutOfBoundsException(getSearchResult().getName(), request.season, getLastSeason(episodes));
				}
				episodes = episodeForSeason;
			}

			return episodes;
		}

		@Override
		public URI getLink() {
			return request.provider.getEpisodeListLink(getSearchResult());
		}

		@Override
		public void process(Collection<Episode> episodes) {
			// set a proper title for the export handler before adding episodes
			getComponent().setTitle(getTitle());

			getComponent().getModel().addAll(episodes);
		}

		@Override
		public String getStatusMessage(Collection<Episode> result) {
			return (result.isEmpty()) ? "No episodes found" : String.format("%d episodes", result.size());
		}

		@Override
		public EpisodeListTab getComponent() {
			return (EpisodeListTab) super.getComponent();
		}

		@Override
		public String getTitle() {
			if (request.season == ALL_SEASONS)
				return super.getTitle();

			// add additional information to default title
			return String.format("%s - Season %d", super.getTitle(), request.season);
		}

		@Override
		public Icon getIcon() {
			return request.provider.getIcon();
		}

		@Override
		protected void configureSelectDialog(SelectDialog<SearchResult> selectDialog) {
			super.configureSelectDialog(selectDialog);
			selectDialog.getMessageLabel().setText("Select a Show:");
		}

	}

	protected static class EpisodeListTab extends FileBotList<Episode> {

		public EpisodeListTab() {
			// initialize dnd and clipboard export handler for episode list
			EpisodeListExportHandler exportHandler = new EpisodeListExportHandler(this);
			setExportHandler(exportHandler);
			getTransferHandler().setClipboardHandler(exportHandler);

			// allow removal of episode list entries
			getRemoveAction().setEnabled(true);

			// remove borders
			listScrollPane.setBorder(null);
			setBorder(null);

			// XXX The user interface of your app is not consistent with the macOS Human Interface Guidelines. Specifically: We found that menu items are not visible, except by right-clicking (see screenshot). See the "WYSIWYG (What You See Is What You Get)," "Give Users
			// Alternate Ways to Accomplish Tasks," and "Designing Contextual Menus" sections of the Human Interface Guidelines.
			if (!isMacSandbox()) {
				JPopupMenu popup = new JPopupMenu("Episodes");

				JMenu menu = new JMenu("Send to");
				for (PanelBuilder panel : PanelBuilder.episodeHandlerSequence()) {
					menu.add(newAction(panel.getName(), panel.getIcon(), evt -> {
						// switch to Rename panel
						SwingEventBus.getInstance().post(panel);

						// load episode data
						invokeLater(200, () -> SwingEventBus.getInstance().post(exportHandler.export(this, false)));
					}));
				}

				popup.add(menu);
				popup.addSeparator();

				popup.add(newAction("Copy", ResourceManager.getIcon("rename.action.copy"), evt -> {
					getTransferHandler().getClipboardHandler().exportToClipboard(this, Toolkit.getDefaultToolkit().getSystemClipboard(), TransferHandler.COPY);
				}));
				popup.add(new SaveAction(getExportHandler()));
				getListComponent().setComponentPopupMenu(popup);
			}
		}

	}

}
