package net.filebot.ui;

import static javax.swing.BorderFactory.*;
import static javax.swing.ScrollPaneConstants.*;
import static net.filebot.Logging.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.matchers.TextMatcherEditor;
import ca.odell.glazedlists.swing.AutoCompleteSupport;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.util.ExceptionUtilities;
import net.filebot.util.ui.LabelProvider;
import net.filebot.util.ui.SelectButton;
import net.filebot.web.SearchResult;
import net.miginfocom.swing.MigLayout;

public abstract class AbstractSearchPanel<S, E> extends JComponent {

	protected final JPanel tabbedPaneGroup = new JPanel(new MigLayout("nogrid, fill, insets 0", "align center", "[fill]8px[pref!]4px"));

	protected final JTabbedPane tabbedPane = new JTabbedPane();

	protected final HistoryPanel historyPanel = new HistoryPanel();

	protected final SelectButtonTextField<S> searchTextField = new SelectButtonTextField<S>();

	protected final BasicEventList<String> searchHistory = new BasicEventList<String>(100000);

	public AbstractSearchPanel() {
		historyPanel.setColumnHeader(2, "Duration");

		JScrollPane historyScrollPane = new JScrollPane(historyPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
		historyScrollPane.setBorder(createEmptyBorder());

		tabbedPane.addTab("History", ResourceManager.getIcon("action.find"), historyScrollPane);

		tabbedPaneGroup.setBorder(createTitledBorder("Search Results"));
		tabbedPaneGroup.add(tabbedPane, "grow, wrap");
		setLayout(new MigLayout("nogrid, novisualpadding, fill, insets 10px 10px 15px 10px", "align 45%", "[pref!]10px[fill]"));

		add(searchTextField, "gap 0px:push");
		add(new JButton(searchAction), "gap 16px, gap after 0px:push, h 2+pref!, id search, sgy button");
		add(tabbedPaneGroup, "newline, grow");

		searchTextField.getEditor().setAction(searchAction);
		searchTextField.getSelectButton().setModel(Arrays.asList(getSearchEngines()));
		searchTextField.getSelectButton().setLabelProvider(getSearchEngineLabelProvider());

		searchTextField.getSelectButton().addPropertyChangeListener(SelectButton.SELECTED_VALUE, new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				new SwingWorker<Collection<String>, Void>() {

					private final S engine = searchTextField.getSelectButton().getSelectedValue();

					@Override
					protected Collection<String> doInBackground() throws Exception {
						TreeSet<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
						set.addAll(getHistory(engine));
						return set;
					}

					@Override
					protected void done() {
						if (engine == searchTextField.getSelectButton().getSelectedValue()) {
							try {
								searchHistory.clear();
								searchHistory.addAll(get());
							} catch (Exception e) {
								debug.log(Level.WARNING, e.getMessage(), e);
							}
						}

					};
				}.execute();
			}
		});

		try {
			// restore selected subtitle client
			searchTextField.getSelectButton().setSelectedIndex(Integer.parseInt(getSettings().get("engine.selected", "0")));
		} catch (Exception e) {
			// log and ignore
			debug.log(Level.WARNING, e.getMessage(), e);
		}

		// save selected client on change
		searchTextField.getSelectButton().getSelectionModel().addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				getSettings().put("engine.selected", Integer.toString(searchTextField.getSelectButton().getSelectedIndex()));
			}
		});

		// high-performance auto-completion
		AutoCompleteSupport<String> acs = AutoCompleteSupport.install(searchTextField.getEditor(), searchHistory);
		acs.setTextMatchingStrategy(TextMatcherEditor.IDENTICAL_STRATEGY);
		acs.setFilterMode(TextMatcherEditor.CONTAINS);
		acs.setCorrectsCase(true);
		acs.setStrict(false);

		installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), searchAction);
	}

	protected abstract Collection<String> getHistory(S engine) throws Exception;

	protected abstract S[] getSearchEngines();

	protected abstract LabelProvider<S> getSearchEngineLabelProvider();

	protected abstract Settings getSettings();

	protected abstract RequestProcessor<?, E> createRequestProcessor();

	private void search(RequestProcessor<?, E> requestProcessor) {
		FileBotTab<?> tab = requestProcessor.tab;

		tab.setTitle(requestProcessor.getTitle());
		tab.setLoading(true);
		tab.setIcon(requestProcessor.getIcon());

		tab.addTo(tabbedPane);

		tabbedPane.setSelectedComponent(tab);

		// search in background
		new SearchTask(requestProcessor).execute();
	}

	private final AbstractAction searchAction = new AbstractAction("Find", ResourceManager.getIcon("action.find")) {

		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getActionCommand() == null || searchTextField.getText().trim().isEmpty()) {
				// command triggered by auto-completion
				return;
			}

			RequestProcessor<?, E> request = createRequestProcessor();
			if (request != null) {
				search(request);
			}
		}
	};

	private class SearchTask extends SwingWorker<Collection<? extends SearchResult>, Void> {

		private final RequestProcessor<?, E> requestProcessor;

		public SearchTask(RequestProcessor<?, E> requestProcessor) {
			this.requestProcessor = requestProcessor;
		}

		@Override
		protected Collection<? extends SearchResult> doInBackground() throws Exception {
			long start = System.currentTimeMillis();

			try {
				return requestProcessor.search();
			} finally {
				requestProcessor.duration += (System.currentTimeMillis() - start);
			}
		}

		@Override
		public void done() {
			FileBotTab<?> tab = requestProcessor.tab;

			// tab might have been closed
			if (tab.isClosed())
				return;

			try {
				Collection<? extends SearchResult> results = get();

				SearchResult selectedSearchResult = null;

				switch (results.size()) {
				case 0:
					log.log(Level.WARNING, String.format("'%s' has not been found.", requestProcessor.request.getSearchText()));
					break;
				case 1:
					selectedSearchResult = results.iterator().next();
					break;
				default:
					selectedSearchResult = requestProcessor.selectSearchResult(results, SwingUtilities.getWindowAncestor(AbstractSearchPanel.this));
					break;
				}

				if (selectedSearchResult == null) {
					tab.close();
					return;
				}

				// set search result
				requestProcessor.setSearchResult(selectedSearchResult);

				tab.setTitle(requestProcessor.getTitle());

				// fetch elements of the selected search result
				new FetchTask(requestProcessor).execute();
			} catch (Exception e) {
				tab.close();
				log.log(Level.WARNING, ExceptionUtilities.getRootCauseMessage(e), e);
			}

		}
	}

	private class FetchTask extends SwingWorker<Collection<E>, Void> {

		private final RequestProcessor<?, E> requestProcessor;

		public FetchTask(RequestProcessor<?, E> requestProcessor) {
			this.requestProcessor = requestProcessor;
		}

		@Override
		protected final Collection<E> doInBackground() throws Exception {
			long start = System.currentTimeMillis();

			try {
				return requestProcessor.fetch();
			} finally {
				requestProcessor.duration += (System.currentTimeMillis() - start);
			}
		}

		@Override
		public void done() {
			FileBotTab<?> tab = requestProcessor.tab;

			if (tab.isClosed())
				return;

			try {
				// check if an exception occurred
				Collection<E> elements = get();

				requestProcessor.process(elements);

				String title = requestProcessor.getTitle();
				Icon icon = requestProcessor.getIcon();
				String statusMessage = requestProcessor.getStatusMessage(elements);

				historyPanel.add(title, requestProcessor.getLink(), icon, statusMessage, String.format("%,d ms", requestProcessor.getDuration()));

				// close tab if no elements were fetched
				if (get().size() <= 0) {
					log.warning(statusMessage);
					tab.close();
				}
			} catch (Exception e) {
				tab.close();
				log.log(Level.WARNING, ExceptionUtilities.getRootCauseMessage(e), e);
			} finally {
				tab.setLoading(false);
			}
		}
	}

	protected static class Request {

		private final String searchText;

		public Request(String searchText) {
			this.searchText = searchText;
		}

		public String getSearchText() {
			return searchText;
		}

	}

	protected abstract static class RequestProcessor<R extends Request, E> {

		protected final R request;

		private FileBotTab<JComponent> tab;

		private SearchResult searchResult;

		private long duration = 0;

		public RequestProcessor(R request, JComponent component) {
			this.request = request;
			this.tab = new FileBotTab<JComponent>(component);
		}

		public abstract Collection<? extends SearchResult> search() throws Exception;

		public abstract Collection<E> fetch() throws Exception;

		public abstract void process(Collection<E> elements);

		public abstract URI getLink();

		public JComponent getComponent() {
			return tab.getComponent();
		}

		public SearchResult getSearchResult() {
			return searchResult;
		}

		public void setSearchResult(SearchResult searchResult) {
			this.searchResult = searchResult;
		}

		public String getStatusMessage(Collection<E> result) {
			return String.format("%d elements found", result.size());
		}

		public String getTitle() {
			if (searchResult != null)
				return searchResult.getName();

			return request.getSearchText();
		}

		public Icon getIcon() {
			return null;
		}

		protected SearchResult selectSearchResult(Collection<? extends SearchResult> searchResults, Window window) throws Exception {
			// multiple results have been found, user must select one
			SelectDialog<SearchResult> selectDialog = new SelectDialog<SearchResult>(window, searchResults);
			configureSelectDialog(selectDialog);

			selectDialog.setVisible(true);

			// selected value or null if the dialog was canceled by the user
			return selectDialog.getSelectedValue();
		}

		protected void configureSelectDialog(SelectDialog<SearchResult> selectDialog) {
			selectDialog.setLocation(getOffsetLocation(selectDialog.getOwner()));
			selectDialog.setIconImage(getImage(getIcon()));
			selectDialog.setMinimumSize(new Dimension(250, 150));
			selectDialog.pack();
		}

		public long getDuration() {
			return duration;
		}
	}

}
