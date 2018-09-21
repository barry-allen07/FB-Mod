package net.filebot.cli;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.media.MediaDetection.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.bundle.LanternaThemes;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.LocalizedString;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Panels;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.Window.Hint;
import com.googlecode.lanterna.gui2.dialogs.ListSelectDialogBuilder;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorAutoCloseTrigger;

import net.filebot.RenameAction;
import net.filebot.similarity.Match;
import net.filebot.web.SearchResult;

public class CmdlineOperationsTextUI extends CmdlineOperations {

	public static final String DEFAULT_THEME = "businessmachine";

	private Terminal terminal;
	private Screen screen;
	private MultiWindowTextGUI ui;

	public CmdlineOperationsTextUI() throws Exception {
		terminal = new DefaultTerminalFactory().setTerminalEmulatorFrameAutoCloseTrigger(TerminalEmulatorAutoCloseTrigger.CloseOnEscape).createTerminal();
		screen = new TerminalScreen(terminal);
		ui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.DEFAULT));

		// use green matrix-style theme
		ui.setTheme(LanternaThemes.getRegisteredTheme(DEFAULT_THEME));
	}

	public synchronized <T> T onScreen(Supplier<T> dialog) throws Exception {
		try {
			screen.startScreen();
			return dialog.get();
		} finally {
			screen.stopScreen();
		}
	}

	@Override
	public List<File> renameAll(Map<File, File> renameMap, RenameAction renameAction, ConflictAction conflictAction, List<Match<File, ?>> matches, ExecCommand exec) throws Exception {
		// default behavior if rename map is empty
		if (renameMap.isEmpty()) {
			return super.renameAll(renameMap, renameAction, conflictAction, matches, exec);
		}

		// manually confirm each file mapping
		String title = String.format("%s / %s", renameAction, conflictAction);

		// Alias.1x01.mp4 => Alias - 1x01 - Pilot.mp4
		int columnSize = renameMap.keySet().stream().mapToInt(f -> f.getName().length()).max().orElse(0);

		Function<Entry<File, File>, String> renderer = m -> String.format("%-" + columnSize + "s\t=>\t%s", m.getKey().getName(), m.getValue().getName());
		Predicate<Entry<File, File>> checked = m -> m.getKey().exists() && !m.getValue().exists();

		List<Entry<File, File>> selection = showInputDialog(renameMap.entrySet(), renderer, checked, title);

		// no selection, do nothing and return successfully
		if (selection == null || selection.isEmpty()) {
			return emptyList();
		}

		return super.renameAll(selection.stream().collect(toMap(Entry::getKey, Entry::getValue, (a, b) -> a, LinkedHashMap::new)), renameAction, conflictAction, matches, exec);
	}

	@Override
	protected <T extends SearchResult> List<T> selectSearchResult(String query, Collection<T> options, boolean sort, boolean alias, boolean strict, int limit) throws Exception {
		List<T> matches = getProbableMatches(sort ? query : null, options, alias, false);

		if (matches.size() <= 1) {
			return matches;
		}

		// manually select option if there is more than one
		T selection = showInputDialog(matches, "Multiple Options", String.format("Select best match for \"%s\"", query));

		if (selection == null) {
			return emptyList();
		}

		return singletonList(selection);
	}

	public <T> T showInputDialog(Collection<T> options, String title, String message) throws Exception {
		return onScreen(() -> {
			ListSelectDialogBuilder<T> dialog = new ListSelectDialogBuilder<T>();
			dialog.setExtraWindowHints(singleton(Hint.CENTERED));

			dialog.setTitle(title);
			dialog.setDescription(message);
			options.forEach(dialog::addListItem);

			return dialog.build().showDialog(ui);
		});
	}

	public <T> List<T> showInputDialog(Collection<T> options, Function<T, String> renderer, Predicate<T> checked, String title) throws Exception {
		return onScreen(() -> {
			List<T> selection = new ArrayList<T>(options.size());

			BasicWindow dialog = new BasicWindow();
			dialog.setTitle(title);
			dialog.setHints(asList(Hint.MODAL, Hint.CENTERED));

			CheckBoxList<CheckBoxListItem> checkBoxList = new CheckBoxList<CheckBoxListItem>();

			for (T option : options) {
				checkBoxList.addItem(new CheckBoxListItem<T>(option, renderer.apply(option)), checked.test(option));
			}

			Button okButton = new Button(LocalizedString.OK.toString(), () -> {
				for (CheckBoxListItem<T> it : checkBoxList.getCheckedItems()) {
					selection.add(it.getValue());
				}
				dialog.close();
			});

			Button cancelButton = new Button(LocalizedString.Cancel.toString(), () -> {
				dialog.close();
			});

			Panel contentPane = new Panel();
			contentPane.setLayoutManager(new GridLayout(1));

			contentPane.addComponent(checkBoxList.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.BEGINNING, true, true, 1, 1)));
			contentPane.addComponent(new Separator(Direction.HORIZONTAL).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER, true, false, 1, 1)));
			contentPane.addComponent(Panels.grid(2, okButton, cancelButton).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false, 1, 1)));

			dialog.setComponent(contentPane);

			ui.addWindowAndWait(dialog);

			return selection;
		});
	}

	protected static class CheckBoxListItem<T> {

		private final T value;
		private final String label;

		public CheckBoxListItem(T value, String label) {
			this.value = value;
			this.label = label;
		}

		public T getValue() {
			return value;
		}

		@Override
		public String toString() {
			return label;
		}

	}

}
