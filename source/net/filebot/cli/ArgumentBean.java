package net.filebot.cli;

import static java.awt.GraphicsEnvironment.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.PGP.*;

import java.io.File;
import java.io.FileFilter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

import net.filebot.ApplicationFolder;
import net.filebot.Language;
import net.filebot.RenameAction;
import net.filebot.StandardRenameAction;
import net.filebot.WebServices;
import net.filebot.format.ExpressionFileFilter;
import net.filebot.format.ExpressionFileFormat;
import net.filebot.format.ExpressionFilter;
import net.filebot.format.ExpressionFormat;
import net.filebot.hash.HashType;
import net.filebot.subtitle.SubtitleFormat;
import net.filebot.subtitle.SubtitleNaming;
import net.filebot.ui.PanelBuilder;
import net.filebot.web.Datasource;
import net.filebot.web.EpisodeListProvider;
import net.filebot.web.SortOrder;

public class ArgumentBean {

	@Option(name = "--mode", usage = "Open GUI in single panel mode / Enable CLI interactive mode", metaVar = "[Rename, Subtitles, SFV] or [interactive]")
	public String mode = null;

	@Option(name = "-rename", usage = "Rename media files")
	public boolean rename = false;

	@Option(name = "--db", usage = "Database", metaVar = "[TheTVDB, AniDB, TheMovieDB::TV] or [TheMovieDB] or [AcoustID, ID3] or [xattr, exif, file]")
	public String db;

	@Option(name = "--order", usage = "Episode order", metaVar = "[Airdate, DVD, Absolute, AbsoluteAirdate]")
	public String order = "Airdate";

	@Option(name = "--action", usage = "Rename action", metaVar = "[move, copy, keeplink, symlink, hardlink, clone, test]")
	public String action = "move";

	@Option(name = "--conflict", usage = "Conflict resolution", metaVar = "[skip, override, auto, index, fail]")
	public String conflict = "skip";

	@Option(name = "--filter", usage = "Match filter expression", metaVar = "{expression}")
	public String filter = null;

	@Option(name = "--format", usage = "Format expression", metaVar = "{expression}")
	public String format;

	@Option(name = "-non-strict", usage = "Enable advanced matching and more aggressive guessing")
	public boolean nonStrict = false;

	@Option(name = "-get-subtitles", usage = "Fetch subtitles")
	public boolean getSubtitles;

	@Option(name = "--q", usage = "Force lookup query", metaVar = "series / movie query")
	public String query;

	@Option(name = "--lang", usage = "Language", metaVar = "language code")
	public String lang = "en";

	@Option(name = "-check", usage = "Create / Check verification files")
	public boolean check;

	@Option(name = "--output", usage = "Output path", metaVar = "path")
	public String output;

	@Option(name = "--encoding", usage = "Output character encoding", metaVar = "[UTF-8, Windows-1252]")
	public String encoding;

	@Option(name = "-list", usage = "Print episode list")
	public boolean list = false;

	@Option(name = "-mediainfo", usage = "Print media info")
	public boolean mediaInfo = false;

	@Option(name = "-revert", usage = "Revert files")
	public boolean revert = false;

	@Option(name = "-extract", usage = "Extract archives")
	public boolean extract = false;

	@Option(name = "-script", usage = "Run Groovy script", metaVar = "[fn:name] or [script.groovy]")
	public String script = null;

	@Option(name = "--def", usage = "Define script variables", handler = BindingsHandler.class)
	public Map<String, String> defines = new LinkedHashMap<String, String>();

	@Option(name = "-r", usage = "Recursively process folders")
	public boolean recursive = false;

	@Option(name = "--file-filter", usage = "Input file filter expression", metaVar = "{expression}")
	public String inputFileFilter = null;

	@Option(name = "-exec", usage = "Execute command", metaVar = "echo {f} [+]", handler = RestOfArgumentsHandler.class)
	public List<String> exec = new ArrayList<String>();

	@Option(name = "-unixfs", usage = "Allow special characters in file paths")
	public boolean unixfs = false;

	@Option(name = "-no-xattr", usage = "Disable extended attributes")
	public boolean disableExtendedAttributes = false;

	@Option(name = "--log", usage = "Log level", metaVar = "[all, fine, info, warning]")
	public String log = "all";

	@Option(name = "--log-file", usage = "Log file", metaVar = "log.txt")
	public String logFile = null;

	@Option(name = "--log-lock", usage = "Lock log file", metaVar = "[yes, no]", handler = ExplicitBooleanOptionHandler.class)
	public boolean logLock = true;

	@Option(name = "-clear-cache", usage = "Clear cached and temporary data")
	public boolean clearCache = false;

	@Option(name = "-clear-prefs", usage = "Clear application settings")
	public boolean clearPrefs = false;

	@Option(name = "-version", usage = "Print version identifier")
	public boolean version = false;

	@Option(name = "-help", usage = "Print this help message")
	public boolean help = false;

	/*@Option(name = "--license", usage = "Import license file", metaVar = "*.psm")
	public String license = null; */

	@Argument
	public List<String> arguments = new ArrayList<String>();

	public boolean runCLI() {
		return rename || getSubtitles || check || list || mediaInfo || revert || extract || script != null; /*|| (license != null && (isHeadless() || System.console() != null))*/
	}

	public boolean isInteractive() {
		return "interactive".equalsIgnoreCase(mode) && System.console() != null;
	}

	public boolean printVersion() {
		return version;
	}

	public boolean printHelp() {
		return help;
	}

	public boolean clearCache() {
		return clearCache;
	}

	public boolean clearUserData() {
		return clearPrefs;
	}

	public List<File> getFiles(boolean resolveFolders) throws Exception {
		if (arguments == null || arguments.isEmpty()) {
			return emptyList();
		}

		// resolve given paths
		List<File> files = new ArrayList<File>();

		for (String it : arguments) {
			// ignore empty arguments
			if (it.trim().isEmpty()) {
				continue;
			}

			// resolve relative paths
			File file = new File(it);

			// since we don't want to follow symlinks, we need to take the scenic route through the Path class
			try {
				file = file.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
			} catch (Exception e) {
				debug.warning(format("Illegal Argument: %s (%s)", e, file));
			}

			if (resolveFolders && file.isDirectory()) {
				if (recursive) {
					files.addAll(listFiles(file, FILES, HUMAN_NAME_ORDER));
				} else {
					files.addAll(getChildren(file, f -> f.isFile() && !f.isHidden(), HUMAN_NAME_ORDER));
				}
			} else {
				files.add(file);
			}
		}

		// input file filter (e.g. useful on Windows where find -exec is not an option)
		if (inputFileFilter != null) {
			return filter(files, new ExpressionFileFilter(inputFileFilter, f -> f));
		}

		return files;
	}

	public RenameAction getRenameAction() {
		// support custom executables (via absolute path)
		if (action.startsWith("/")) {
			return new ExecutableRenameAction(action, getOutputPath());
		}

		// support custom groovy scripts (via closures)
		if (action.startsWith("{")) {
			return new GroovyRenameAction(action);
		}

		return StandardRenameAction.forName(action);
	}

	public ConflictAction getConflictAction() {
		return ConflictAction.forName(conflict);
	}

	public SortOrder getSortOrder() {
		return SortOrder.forName(order);
	}

	public ExpressionFormat getExpressionFormat() throws Exception {
		return format == null ? null : new ExpressionFormat(format);
	}

	public ExpressionFileFormat getExpressionFileFormat() throws Exception {
		return format == null ? null : new ExpressionFileFormat(format);
	}

	public ExpressionFilter getExpressionFilter() throws Exception {
		return filter == null ? null : new ExpressionFilter(filter);
	}

	public FileFilter getFileFilter() throws Exception {
		return filter == null ? FILES : new ExpressionFileFilter(filter, xattr::getMetaInfo);
	}

	public Datasource getDatasource() {
		return db == null ? null : WebServices.getService(db);
	}

	public EpisodeListProvider getEpisodeListProvider() {
		return db == null ? WebServices.TheTVDB : WebServices.getEpisodeListProvider(db); // default to TheTVDB if --db is not set
	}

	public String getSearchQuery() {
		return query == null || query.isEmpty() ? null : query;
	}

	public File getOutputPath() {
		return output == null ? null : new File(output);
	}

	public File getAbsoluteOutputFolder() throws Exception {
		return output == null ? null : new File(output).getCanonicalFile();
	}

	public SubtitleFormat getSubtitleOutputFormat() {
		return output == null ? null : getSubtitleFormatByName(output);
	}

	public SubtitleNaming getSubtitleNamingFormat() {
		return optional(format).map(SubtitleNaming::forName).orElse(SubtitleNaming.MATCH_VIDEO_ADD_LANGUAGE_TAG);
	}

	public HashType getOutputHashType() {
		// support --output checksum.sfv
		return optional(output).map(File::new).map(f -> getHashType(f)).orElseGet(() -> {
			// support --format SFV
			return optional(format).map(k -> getHashTypeByExtension(k)).orElse(HashType.SFV);
		});
	}

	public Charset getEncoding() {
		return encoding == null ? null : Charset.forName(encoding);
	}

	public Language getLanguage() {
		// find language code for any input (en, eng, English, etc)
		return optional(lang).map(Language::findLanguage).orElseThrow(error("Illegal language code", lang));
	}

	public File getLogFile() {
		File file = new File(logFile);

		if (file.isAbsolute()) {
			return file;
		}

		// by default resolve relative paths against {applicationFolder}/logs/{logFile}
		return ApplicationFolder.AppData.resolve("logs/" + logFile);
	}

	public boolean isStrict() {
		return !nonStrict;
	}

	public Level getLogLevel() {
		return Level.parse(log.toUpperCase());
	}

	public ExecCommand getExecCommand() {
		try {
			return exec == null || exec.isEmpty() ? null : ExecCommand.parse(exec, getOutputPath());
		} catch (Exception e) {
			throw new CmdlineException("Illegal exec expression: " + exec);
		}
	}

	public PanelBuilder[] getPanelBuilders() {
		// default multi panel mode
		if (mode == null) {
			return PanelBuilder.defaultSequence();
		}

		// only selected panels
		return optional(mode).map(m -> {
			Pattern pattern = Pattern.compile(mode, Pattern.CASE_INSENSITIVE);
			PanelBuilder[] panel = stream(PanelBuilder.defaultSequence()).filter(p -> pattern.matcher(p.getName()).matches()).toArray(PanelBuilder[]::new);

			// throw exception if illegal pattern was passed in
			if (panel.length == 0) {
				return null;
			}

			return panel;
		}).orElseThrow(error("Illegal mode", mode));
	}

	/*public String getLicenseKey() {
		try {
			return license == null || license.isEmpty() ? null : findClearSignMessage(readTextFile(new File(license)));
		} catch (Exception e) {
			throw new CmdlineException("Invalid License File: " + e.getMessage(), e);
		}
	} */

	private final String[] args;

	public ArgumentBean() {
		this.args = new String[0];
	}

	public ArgumentBean(String[] args) throws CmdLineException {
		this.args = args.clone();

		CmdLineParser parser = new CmdLineParser(this);
		parser.parseArgument(args);
	}

	public String[] getArgumentArray() {
		return args.clone();
	}

	public String usage() {
		StringWriter buffer = new StringWriter();
		CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withShowDefaults(false).withOptionSorter(null));
		parser.printUsage(buffer, null);
		return buffer.toString();
	}

	private static <T> Optional<T> optional(T value) {
		return Optional.ofNullable(value);
	}

	private static Supplier<CmdlineException> error(String message, Object value) {
		return () -> new CmdlineException(message + ": " + value);
	}

	@Override
	public String toString() {
		return deepToString(args);
	}

	public static ArgumentBean parse(String... args) throws CmdLineException {
		try {
			return new ArgumentBean(args);
		} catch (CmdLineException e) {
			// MAS does not support or allow command-line applications and may run executables with strange arguments for no apparent reason (e.g. filebot.launcher -psn_0_774333) so we ignore arguments completely in this case
			if (Boolean.parseBoolean(System.getProperty("apple.app.launcher"))) {
				return new ArgumentBean();
			}

			// just throw exception as usual when called from command-line and display argument errors
			throw e;
		}
	}

}
