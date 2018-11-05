package net.filebot.ui.rename;

import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.WebServices.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.CachedResource.Transform;
import net.filebot.Language;
import net.filebot.StandardRenameAction;
import net.filebot.format.ExpressionFileFilter;
import net.filebot.format.ExpressionFileFormat;
import net.filebot.format.ExpressionFilter;
import net.filebot.format.ExpressionFormat;
import net.filebot.media.LocalDatasource;
import net.filebot.web.Datasource;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.MovieIdentificationService;
import net.filebot.web.MusicIdentificationService;
import net.filebot.web.SortOrder;

public class Preset {

	public String name;
	public String path;
	public String includes;
	public String format;
	public String database;
	public String sortOrder;
	public String matchMode;
	public String language;
	public String action;

	public Preset(String name, File path, ExpressionFilter includes, ExpressionFormat format, Datasource database, SortOrder sortOrder, String matchMode, Language language, StandardRenameAction action) {
		this.name = name;
		this.path = path == null ? null : path.getPath();
		this.includes = includes == null ? null : includes.getExpression();
		this.format = format == null ? null : format.getExpression();
		this.database = database == null ? null : database.getIdentifier();
		this.sortOrder = sortOrder == null ? null : sortOrder.name();
		this.matchMode = matchMode == null ? null : matchMode;
		this.language = language == null ? null : language.getCode();
		this.action = action == null ? null : action.name();
	}

	public String getName() {
		return name;
	}

	public File getInputFolder() {
		return getValue(path, File::new);
	}

	public ExpressionFileFilter getIncludeFilter() {
		return getInputFolder() == null ? null : getValue(includes, expression -> new ExpressionFileFilter(expression, xattr::getMetaInfo));
	}

	public ExpressionFileFormat getFormat() {
		return getValue(format, ExpressionFileFormat::new);
	}

	public String getMatchMode() {
		return getValue(matchMode, mode -> mode);
	}

	public SortOrder getSortOrder() {
		return getValue(sortOrder, SortOrder::forName);
	}

	public Language getLanguage() {
		return getValue(language, Language::getLanguage);
	}

	public StandardRenameAction getRenameAction() {
		return getValue(action, StandardRenameAction::forName);
	}

	public Datasource getDatasource() {
		return getValue(database, id -> getService(id, getSupportedServices()));
	}

	public Icon getIcon() {
		return getValue(database, id -> getService(id, getSupportedServices()).getIcon());
	}

	private <T> T getValue(String s, Transform<String, T> t) {
		try {
			return s == null || s.isEmpty() ? null : t.transform(s);
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
		return null;
	}

	public List<File> selectFiles() {
		File folder = getInputFolder();
		if (folder == null || !folder.isDirectory()) {
			return emptyList();
		}

		FileFilter filter = getIncludeFilter();

		return listFiles(folder, filter == null ? FILES : f -> FILES.accept(f) && filter.accept(f), HUMAN_NAME_ORDER);
	}

	public AutoCompleteMatcher getAutoCompleteMatcher() {
		Datasource db = getDatasource();

		if (db instanceof MovieIdentificationService) {
			return new MovieMatcher((MovieIdentificationService) db);
		}

		if (db instanceof EpisodeListProvider) {
			return new EpisodeListMatcher((EpisodeListProvider) db, db == AniDB);
		}

		if (db instanceof MusicIdentificationService) {
			return new MusicMatcher((MusicIdentificationService) db);
		}

		if (db instanceof LocalDatasource) {
			return new LocalFileMatcher((LocalDatasource) db);
		}

		throw new IllegalStateException("Illegal datasource: " + db);
	}

	@Override
	public String toString() {
		return name;
	}

	public static Datasource[] getSupportedServices() {
		return Stream.of(getEpisodeListProviders(), getMovieIdentificationServices(), getMusicIdentificationServices(), getLocalDatasources()).flatMap(Stream::of).toArray(Datasource[]::new);
	}

	public static StandardRenameAction[] getSupportedActions() {
		if (isWindowsApp()) {
			// CoW clones not supported on Windows
			return new StandardRenameAction[] { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.KEEPLINK, StandardRenameAction.SYMLINK, StandardRenameAction.HARDLINK };
		} else {
			// CoW clones / reflinks supported on macOS and Linux
			return new StandardRenameAction[] { StandardRenameAction.MOVE, StandardRenameAction.COPY, StandardRenameAction.KEEPLINK, StandardRenameAction.SYMLINK, StandardRenameAction.HARDLINK, StandardRenameAction.CLONE };
		}

	}

	public static Language[] getSupportedLanguages() {
		return Stream.of(Language.preferredLanguages(), Language.availableLanguages()).flatMap(List::stream).toArray(Language[]::new);
	}

	public static String[] getSupportedMatchModes() {
		return new String[] { RenamePanel.MATCH_MODE_OPPORTUNISTIC, RenamePanel.MATCH_MODE_STRICT };
	}

}
