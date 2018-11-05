package net.filebot.cli;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.media.XattrMetaInfo.*;
import static net.filebot.util.FileUtilities.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import com.sun.jna.Platform;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import groovy.xml.MarkupBuilder;
import net.filebot.HistorySpooler;
import net.filebot.RenameAction;
import net.filebot.StandardRenameAction;
import net.filebot.WebServices;
import net.filebot.format.AssociativeScriptObject;
import net.filebot.format.ExpressionFormat;
import net.filebot.format.MediaBindingBean;
import net.filebot.format.SuppressedThrowables;
import net.filebot.media.MediaDetection;
import net.filebot.similarity.SeasonEpisodeMatcher.SxE;
import net.filebot.web.Movie;

public abstract class ScriptShellBaseClass extends Script {

	private final Map<String, Object> defaultValues = synchronizedMap(new LinkedHashMap<String, Object>());

	public void setDefaultValues(Map<String, ?> values) {
		defaultValues.putAll(values);
	}

	public Map<String, Object> getDefaultValues() {
		return defaultValues;
	}

	@Override
	public Object getProperty(String property) {
		try {
			return super.getProperty(property);
		} catch (MissingPropertyException e) {
			// try user-defined default values (support null values)
			if (defaultValues.containsKey(property)) {
				return defaultValues.get(property);
			}

			// can't use default value, rethrow original exception
			throw e;
		}
	}

	private ArgumentBean getArgumentBean() {
		return (ArgumentBean) getBinding().getVariable(ScriptShell.SHELL_ARGS_BINDING_NAME);
	}

	private ScriptShell getShell() {
		return (ScriptShell) getBinding().getVariable(ScriptShell.SHELL_BINDING_NAME);
	}

	private CmdlineInterface getCLI() {
		return (CmdlineInterface) getBinding().getVariable(ScriptShell.SHELL_CLI_BINDING_NAME);
	}

	public void include(String input) throws Throwable {
		try {
			executeScript(input, null, null, null);
		} catch (Exception e) {
			printException(e, true);
		}
	}

	public Object runScript(String input, String... argv) throws Throwable {
		try {
			ArgumentBean args = argv == null || argv.length == 0 ? getArgumentBean() : new ArgumentBean(argv);
			return executeScript(input, asList(getArgumentBean().getArgumentArray()), args.defines, args.getFiles(false));
		} catch (Exception e) {
			printException(e, true);
		}
		return null;
	}

	public Object executeScript(String input, Map<String, ?> bindings, Object... args) throws Throwable {
		return executeScript(input, asList(getArgumentBean().getArgumentArray()), bindings, asFileList(args));
	}

	public Object executeScript(String input, List<String> argv, Map<String, ?> bindings, List<?> args) throws Throwable {
		// apply parent script defines
		Bindings parameters = new SimpleBindings();

		// initialize default parameter
		if (bindings != null) {
			parameters.putAll(bindings);
		}

		parameters.put(ScriptShell.SHELL_ARGS_BINDING_NAME, argv != null ? new ArgumentBean(argv.toArray(new String[0])) : new ArgumentBean());
		parameters.put(ScriptShell.ARGV_BINDING_NAME, args != null ? asFileList(args) : new ArrayList<File>());

		// run given script
		return getShell().runScript(input, parameters);
	}

	/**public Object getLicense() {
		try {
			return LICENSE.check();
		} catch (Throwable e) {
			printException(e, false);
			return null;
		}
	}*/

	public Object tryQuietly(Closure<?> c) {
		try {
			return c.call();
		} catch (Exception e) {
			return null;
		}
	}

	public Object tryLogCatch(Closure<?> c) {
		try {
			return c.call();
		} catch (Exception e) {
			printException(e, false);
			return null;
		}
	}

	public void printException(Throwable t) {
		printException(t, false);
	}

	public void printException(Throwable t, boolean severe) {
		if (severe) {
			log.log(Level.SEVERE, trace(t));
		} else {
			log.log(Level.WARNING, cause(t));
		}

		// print full stack trace if debug logging is enabled
		debug.log(Level.ALL, "Suppressed Exception: " + t, t);
	}

	public void die(Object cause) throws Throwable {
		if (cause instanceof Throwable) {
			throw new ScriptDeath((Throwable) cause);
		}
		throw new ScriptDeath(String.valueOf(cause));
	}

	// define global variable: _args
	public ArgumentBean get_args() {
		return getArgumentBean();
	}

	// define global variable: _def
	public Map<String, String> get_def() {
		return unmodifiableMap(getArgumentBean().defines);
	}

	// define global variable: _system
	public AssociativeScriptObject get_system() {
		return new AssociativeScriptObject(System.getProperties(), property -> null);
	}

	// define global variable: _environment
	public AssociativeScriptObject get_environment() {
		return new AssociativeScriptObject(System.getenv(), property -> null);
	}

	// Complete or session rename history
	public Map<File, File> getRenameLog() throws IOException {
		return HistorySpooler.getInstance().getSessionHistory().getRenameMap();
	}

	public Map<File, File> getPersistentRenameLog() throws IOException {
		return HistorySpooler.getInstance().getCompleteHistory().getRenameMap();
	}

	public Map<File, File> getRenameLog(boolean complete) throws IOException {
		if (complete) {
			return HistorySpooler.getInstance().getCompleteHistory().getRenameMap();
		} else {
			return HistorySpooler.getInstance().getSessionHistory().getRenameMap();
		}
	}

	// define global variable: log
	public Logger getLog() {
		return log;
	}

	// define global variable: console
	public Object getConsole() {
		return System.console() != null ? System.console() : PseudoConsole.getSystemConsole();
	}

	public Date getNow() {
		return new Date();
	}

	@Override
	public Object run() {
		return null;
	}

	public String getMediaInfo(File file, String format) throws Exception {
		ExpressionFormat formatter = new ExpressionFormat(format);

		try {
			return formatter.format(new MediaBindingBean(xattr.getMetaInfo(file), file));
		} catch (SuppressedThrowables e) {
			debug.finest(format("%s => %s", format, e));
		}

		return null;
	}

	public String detectSeriesName(Object files) throws Exception {
		return detectSeriesName(files, false);
	}

	public String detectAnimeName(Object files) throws Exception {
		return detectSeriesName(files, true);
	}

	public String detectSeriesName(Object files, boolean anime) throws Exception {
		List<File> input = asFileList(files);
		if (input.isEmpty())
			return null;

		List<String> names = MediaDetection.detectSeriesNames(input, anime, Locale.ENGLISH);
		return names == null || names.isEmpty() ? null : names.get(0);
	}

	public static SxE parseEpisodeNumber(Object object) {
		List<SxE> matches = MediaDetection.parseEpisodeNumber(object.toString(), true);
		return matches == null || matches.isEmpty() ? null : matches.get(0);
	}

	public Movie detectMovie(File file, boolean strict) {
		// 1. xattr
		Object metaObject = xattr.getMetaInfo(file);
		if (metaObject instanceof Movie) {
			return (Movie) metaObject;
		}

		// 2. perfect filename match
		try {
			Movie match = MediaDetection.matchMovie(file, 4);
			if (match != null) {
				return match;
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e::toString); // ignore and move on
		}

		// 3. run full-fledged movie detection
		try {
			List<Movie> options = MediaDetection.detectMovieWithYear(file, WebServices.TheMovieDB, Locale.US, strict);
			if (options != null && options.size() > 0) {
				return options.get(0);
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e::toString); // ignore and fail
		}

		return null;
	}

	public Movie matchMovie(String name) {
		List<Movie> matches = MediaDetection.matchMovieName(singleton(name), true, 0);
		return matches == null || matches.isEmpty() ? null : matches.get(0);
	}

	public int execute(Object... args) throws Exception {
		Stream<String> cmd = stream(args).filter(Objects::nonNull).map(Objects::toString);

		if (Platform.isWindows()) {
			// normalize file separator for windows and run with powershell so any
			// executable in PATH will just work
			cmd = Stream.concat(Stream.of("powershell", "-NonInteractive", "-NoProfile", "-NoLogo", "-ExecutionPolicy", "Bypass", "-Command"), cmd);
		} else if (args.length == 1) {
			// make Unix shell parse arguments
			cmd = Stream.concat(Stream.of("sh", "-c"), cmd);
		}

		ProcessBuilder process = new ProcessBuilder(cmd.collect(toList())).inheritIO();
		return process.start().waitFor();
	}

	public String XML(Closure<?> buildClosure) {
		StringWriter out = new StringWriter();
		MarkupBuilder builder = new MarkupBuilder(out);
		buildClosure.rehydrate(buildClosure.getDelegate(), builder, builder).call(); // call closure in MarkupBuilder context
		return out.toString();
	}

	public void telnet(String host, int port, Closure<?> handler) throws IOException {
		try (Socket socket = new Socket(host, port)) {
			handler.call(new PrintStream(socket.getOutputStream(), true, "UTF-8"), new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8")));
		}
	}

	/**
	 * Retry given closure until it returns successfully (indefinitely if -1 is
	 * passed as retry count)
	 */
	public Object retry(int retryCountLimit, int retryWaitTime, Closure<?> c) throws InterruptedException {
		for (int i = 0; retryCountLimit < 0 || i <= retryCountLimit; i++) {
			try {
				return c.call();
			} catch (Exception e) {
				if (i >= 0 && i >= retryCountLimit) {
					throw e;
				}
				Thread.sleep(retryWaitTime);
			}
		}
		return null;
	}

	public List<File> rename(Map<String, ?> parameters) throws Exception {
		// consume all parameters
		List<File> files = getInputFileList(parameters);
		Map<File, File> map = files.isEmpty() ? getInputFileMap(parameters) : emptyMap(); // check map parameter if file/folder is not set
		RenameAction action = getRenameAction(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			if (files.size() > 0) {
				return getCLI().rename(files, action, args.getConflictAction(), args.getAbsoluteOutputFolder(), args.getExpressionFileFormat(), args.getDatasource(), args.getSearchQuery(), args.getSortOrder(), args.getExpressionFilter(), args.getLanguage().getLocale(), args.isStrict(), args.getExecCommand());
			}

			if (map.size() > 0) {
				return getCLI().rename(map, action, args.getConflictAction());
			}
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public List<File> getSubtitles(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().getSubtitles(files, args.getSearchQuery(), args.getLanguage(), args.getSubtitleOutputFormat(), args.getEncoding(), args.getSubtitleNamingFormat(), args.isStrict());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public List<File> getMissingSubtitles(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().getMissingSubtitles(files, args.getSearchQuery(), args.getLanguage(), args.getSubtitleOutputFormat(), args.getEncoding(), args.getSubtitleNamingFormat(), args.isStrict());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public boolean check(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);

		try {
			return getCLI().check(files);
		} catch (Exception e) {
			printException(e);
		}

		return false;
	}

	public File compute(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().compute(files, args.getOutputPath(), args.getOutputHashType(), args.getEncoding());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public List<File> extract(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);
		FileFilter filter = getFileFilter(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().extract(files, args.getOutputPath(), args.getConflictAction(), filter, args.isStrict());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public List<String> fetchEpisodeList(Map<String, ?> parameters) throws Exception {
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().fetchEpisodeList(args.getEpisodeListProvider(), args.getSearchQuery(), args.getExpressionFormat(), args.getExpressionFilter(), args.getSortOrder(), args.getLanguage().getLocale(), args.isStrict()).collect(toList());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	public Object getMediaInfo(Map<String, ?> parameters) throws Exception {
		List<File> files = getInputFileList(parameters);
		ArgumentBean args = getArgumentBean(parameters);

		try {
			return getCLI().getMediaInfo(files, args.getFileFilter(), args.getExpressionFormat());
		} catch (Exception e) {
			printException(e);
		}

		return null;
	}

	private ArgumentBean getArgumentBean(Map<String, ?> parameters) throws Exception {
		// clone default arguments
		ArgumentBean args = new ArgumentBean(getArgumentBean().getArgumentArray());

		// for compatibility reasons [forceExtractAll: true] and [strict: true] is the
		// same as -non-strict
		Stream.of("forceExtractAll", "strict").map(parameters::remove).filter(Objects::nonNull).forEach(v -> {
			args.nonStrict = !DefaultTypeTransformation.castToBoolean(v);
		});

		// override default values with given values
		parameters.forEach((k, v) -> {
			try {
				Field field = args.getClass().getField(k);
				Object value = DefaultTypeTransformation.castToType(v, field.getType());
				field.set(args, value);
			} catch (Exception e) {
				throw new IllegalArgumentException("Illegal parameter: " + k, e);
			}
		});

		return args;
	}

	private List<File> getInputFileList(Map<String, ?> parameters) {
		// check file parameter add consume File values as they are
		return consumeParameter(parameters, "file").map(f -> asFileList(f)).findFirst().orElseGet(() -> {
			// check folder parameter and resolve children
			return consumeParameter(parameters, "folder").flatMap(f -> asFileList(f).stream()).flatMap(f -> getChildren(f, FILES, HUMAN_NAME_ORDER).stream()).collect(toList());
		});
	}

	private Map<File, File> getInputFileMap(Map<String, ?> parameters) {
		// convert keys and values to files
		Map<File, File> map = new LinkedHashMap<File, File>();

		consumeParameter(parameters, "map").map(Map.class::cast).forEach(m -> {
			m.forEach((k, v) -> {
				File from = asFileList(k).get(0);
				File to = asFileList(v).get(0);
				map.put(from, to);
			});
		});

		return map;
	}

	private RenameAction getRenameAction(Map<String, ?> parameters) {
		return consumeParameter(parameters, "action").map(action -> {
			return getRenameAction(action);
		}).findFirst().orElse(getArgumentBean().getRenameAction()); // default to global rename action
	}

	private FileFilter getFileFilter(Map<String, ?> parameters) {
		return consumeParameter(parameters, "filter").map(filter -> {
			return (FileFilter) DefaultTypeTransformation.castToType(filter, FileFilter.class);
		}).findFirst().orElse(null);
	}

	private Stream<?> consumeParameter(Map<String, ?> parameters, String... names) {
		return Stream.of(names).map(parameters::remove).filter(Objects::nonNull);
	}

	public RenameAction getRenameAction(Object obj) {
		if (obj instanceof RenameAction) {
			return (RenameAction) obj;
		}

		if (obj instanceof CharSequence) {
			return StandardRenameAction.forName(obj.toString());
		}

		if (obj instanceof File) {
			return new ExecutableRenameAction(obj.toString(), getArgumentBean().getOutputPath());
		}

		if (obj instanceof Closure) {
			return new GroovyRenameAction((Closure) obj);
		}

		// object probably can't be casted
		return (RenameAction) DefaultTypeTransformation.castToType(obj, RenameAction.class);
	}

	public <T> T showInputDialog(Collection<T> options, String title, String message) throws Exception {
		if (options.isEmpty()) {
			return null;
		}

		// use Text UI in interactive mode
		if (getCLI() instanceof CmdlineOperationsTextUI) {
			CmdlineOperationsTextUI cli = (CmdlineOperationsTextUI) getCLI();
			return cli.showInputDialog(options, title, message);
		}

		// use Swing dialog non-headless environments
		if (!java.awt.GraphicsEnvironment.isHeadless()) {
			List<T> selection = new ArrayList<T>(1);
			javax.swing.SwingUtilities.invokeAndWait(() -> {
				T value = (T) javax.swing.JOptionPane.showInputDialog(null, message, title, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options.toArray(), options.iterator().next());
				selection.add(0, value);
			});
			return selection.get(0);
		}

		// just pick the first option if we can't ask the user
		log.log(Level.CONFIG, format("Auto-Select [%s] from %s", options.iterator().next(), options));
		return options.iterator().next();
	}

}
