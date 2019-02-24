package net.filebot.ui.subtitle.upload;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.media.MediaDetection.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import net.filebot.Language;
import net.filebot.ResourceManager;
import net.filebot.WebServices;
import net.filebot.media.MediaDetection;
import net.filebot.util.ui.EmptySelectionModel;
import net.filebot.web.Movie;
import net.filebot.web.OpenSubtitlesClient;
import net.filebot.web.SearchResult;
import net.filebot.web.TheTVDBSeriesInfo;
import net.filebot.web.VideoHashSubtitleService.CheckResult;
import net.miginfocom.swing.MigLayout;
import one.util.streamex.StreamEx;

public class SubtitleUploadDialog extends JDialog {

	private final JTable subtitleMappingTable;

	private final OpenSubtitlesClient database;

	private ExecutorService checkExecutorService = Executors.newSingleThreadExecutor();
	private ExecutorService uploadExecutorService;

	public SubtitleUploadDialog(OpenSubtitlesClient database, Window owner) {
		super(owner, "Upload Subtitles", ModalityType.DOCUMENT_MODAL);

		this.database = database;
		subtitleMappingTable = createTable();

		JComponent content = (JComponent) getContentPane();
		content.setLayout(new MigLayout("fill, insets dialog, nogrid, novisualpadding", "", "[fill][pref!]"));

		content.add(new JScrollPane(subtitleMappingTable), "grow, wrap");

		content.add(newButton("Upload", ResourceManager.getIcon("dialog.continue"), this::doUpload), "tag ok");
		content.add(newButton("Close", ResourceManager.getIcon("dialog.cancel"), this::doClose), "tag cancel");
	}

	protected JTable createTable() {
		JTable table = new JTable(new SubtitleMappingTableModel());
		table.setDefaultRenderer(Movie.class, new MovieRenderer(database.getIcon()));
		table.setDefaultRenderer(File.class, new FileRenderer());
		table.setDefaultRenderer(Language.class, new LanguageRenderer());
		table.setDefaultRenderer(Status.class, new StatusRenderer());

		table.setRowHeight(28);
		table.setIntercellSpacing(new Dimension(5, 5));

		table.setBackground(Color.white);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);

		// disable selection
		table.setSelectionModel(new EmptySelectionModel());

		table.setDefaultEditor(Movie.class, new MovieEditor(database));
		table.setDefaultEditor(File.class, new FileEditor());
		table.setDefaultEditor(Language.class, new LanguageEditor());

		return table;
	}

	public void setUploadPlan(Map<File, File> uploadPlan) {
		List<SubtitleMapping> mappings = new ArrayList<SubtitleMapping>(uploadPlan.size());
		for (Entry<File, File> entry : uploadPlan.entrySet()) {
			File subtitle = entry.getKey();
			File video = entry.getValue();

			Locale locale = MediaDetection.guessLanguageFromSuffix(subtitle);
			Language language = Language.getLanguage(locale);

			mappings.add(new SubtitleMapping(subtitle, video, language));
		}

		subtitleMappingTable.setModel(new SubtitleMappingTableModel(mappings).onCheckPending(this::startChecking));
	}

	public void startChecking() {
		for (SubtitleMapping mapping : ((SubtitleMappingTableModel) subtitleMappingTable.getModel()).getData()) {
			if (mapping.isCheckReady()) {
				checkExecutorService.submit(() -> runCheck(mapping));
			}
		}
	}

	private final Pattern CDI_PATTERN = Pattern.compile("(?<!\\p{Alnum})CD\\D?(?<i>[1-9])(?!\\p{Digit})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

	private int getCD(SubtitleMapping mapping) {
		int i = Integer.MIN_VALUE;
		for (File f : new File[] { mapping.getSubtitle(), mapping.getVideo() }) {
			Matcher m = CDI_PATTERN.matcher(f.getName());
			while (m.find()) {
				i = Integer.parseInt(m.group("i"));
			}
		}
		return i;
	}

	private List<SubtitleGroup> getUploadGroups(SubtitleMapping[] table) {
		return StreamEx.ofValues(StreamEx.of(table).groupingBy(SubtitleMapping::getGroup, LinkedHashMap::new, toList())).flatMap(this::groupRunsByCD).toList();
	}

	private Stream<SubtitleGroup> groupRunsByCD(Collection<SubtitleMapping> group) {
		return StreamEx.of(group).sortedBy(SubtitleMapping::getVideo).groupRuns((m1, m2) -> getCD(m1) + 1 == getCD(m2)).map(SubtitleGroup::new);
	}

	private void runCheck(SubtitleMapping mapping) {
		try {

			if (mapping.getIdentity() == null && mapping.getVideo() != null) {
				mapping.setState(Status.Checking);

				CheckResult checkResult = database.checkSubtitle(mapping.getVideo(), mapping.getSubtitle());

				// force upload all subtitles regardless of what TryUploadSubtitles returns (because other programs often submit crap)
				if (checkResult.exists) {
					mapping.setLanguage(Language.getLanguage(checkResult.language)); // trust language hint only if upload not required
				}
			}

			if (mapping.getLanguage() == null) {
				mapping.setState(Status.Identifying);
				try {
					Locale locale = database.detectLanguage(readFile(mapping.getSubtitle()));
					mapping.setLanguage(Language.getLanguage(locale));
				} catch (Exception e) {
					debug.log(Level.WARNING, "Failed to auto-detect language: " + e.getMessage());
				}
			}

			if (mapping.getIdentity() == null && mapping.getVideo() != null) {
				mapping.setState(Status.Identifying);
				try {
					if (MediaDetection.isEpisode(mapping.getVideo().getPath(), true)) {
						List<String> seriesNames = MediaDetection.detectSeriesNames(singleton(mapping.getVideo()), false, Locale.ENGLISH);
						NAMES: for (String name : seriesNames) {
							List<SearchResult> options = WebServices.TheTVDB.search(name, Locale.ENGLISH);
							for (SearchResult entry : options) {
								TheTVDBSeriesInfo seriesInfo = WebServices.TheTVDB.getSeriesInfo(entry, Locale.ENGLISH);
								if (seriesInfo.getImdbId() != null) {
									int imdbId = grepImdbId(seriesInfo.getImdbId()).iterator().next();
									mapping.setIdentity(WebServices.OpenSubtitles.getMovieDescriptor(new Movie(imdbId), Locale.ENGLISH));
									break NAMES;
								}
							}
						}
					} else {
						Collection<Movie> identity = MediaDetection.detectMovie(mapping.getVideo(), database, Locale.ENGLISH, true);
						for (Movie it : identity) {
							if (it.getImdbId() <= 0 && it.getTmdbId() > 0) {
								it = WebServices.TheMovieDB.getMovieDescriptor(it, Locale.US);
							}
							if (it != null && it.getImdbId() > 0) {
								mapping.setIdentity(it);
								break;
							}
						}
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, "Failed to auto-detect movie: " + e.getMessage());
				}
			}

			if (mapping.getVideo() == null) {
				mapping.setState(Status.IllegalInput);
			} else if (mapping.getIdentity() == null || mapping.getLanguage() == null) {
				mapping.setState(Status.IdentificationRequired);
			} else {
				mapping.setState(Status.UploadReady);
			}
		} catch (Exception e) {
			debug.log(Level.SEVERE, e.getMessage(), e);
			mapping.setState(Status.CheckFailed);
		}
	}

	private void runUpload(SubtitleGroup group) {
		try {
			group.setState(Status.Uploading);
			database.uploadSubtitle(group.getIdentity(), group.getLanguage().getLocale(), group.getVideoFiles(), group.getSubtitleFiles());
			group.setState(Status.UploadComplete);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e.getMessage(), e);
			group.setState(Status.UploadFailed);
		}
	}

	public void doUpload(ActionEvent evt) {
		// disable any active cell editor
		if (subtitleMappingTable.getCellEditor() != null) {
			subtitleMappingTable.getCellEditor().stopCellEditing();
		}

		// don't allow restart of upload as long as there are still unfinished download tasks
		if (uploadExecutorService != null && !uploadExecutorService.isTerminated()) {
			return;
		}

		uploadExecutorService = Executors.newSingleThreadExecutor();

		SubtitleMapping[] table = ((SubtitleMappingTableModel) subtitleMappingTable.getModel()).getData();
		for (SubtitleGroup group : getUploadGroups(table)) {
			if (group.isUploadReady()) {
				uploadExecutorService.submit(() -> runUpload(group));
			}
		}

		// terminate after all uploads have been completed
		uploadExecutorService.shutdown();
	}

	public void doClose(ActionEvent evt) {
		if (checkExecutorService != null) {
			checkExecutorService.shutdownNow();
		}
		if (uploadExecutorService != null) {
			uploadExecutorService.shutdownNow();
		}

		setVisible(false);
		dispose();
	}

}
