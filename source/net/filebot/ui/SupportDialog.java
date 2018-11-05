package net.filebot.ui;

import static java.util.stream.Collectors.*;
import static javax.swing.JOptionPane.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.StringUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import net.filebot.HistorySpooler;
import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.util.PreferencesMap.PreferencesEntry;

public enum SupportDialog {

	AppStoreReview {

		@Override
		String getMessage(int renameCount) {
			return String.format("<html><p style='font-size:16pt; font-weight:bold'>Thank you for using FileBot!</p><br><p>It has taken thousands of hours to develop this application. If you enjoy using it,<br>please consider writing a nice review on the %s.<p><p style='font-size:14pt; font-weight:bold'>You've renamed %,d files.</p><br><html>", getAppStoreName(), renameCount);
		}

		@Override
		String[] getActions(boolean first) {
			if (first)
				return new String[] { "Write a Review! :)", "Nope! Maybe next time." };
			else
				return new String[] { "Update my Review! :)", "Nope! Not this time." };
		}

		@Override
		Icon getIcon() {
			return ResourceManager.getIcon("window.icon.large");
		}

		@Override
		String getTitle() {
			return "Please write a Review";
		}

		@Override
		public boolean feelingLucky(int sessionRenameCount, int totalRenameCount, int currentRevision, int lastSupportRevision, int supportRevisionCount) {
			// ask for reviews at most once per revision
			if (currentRevision <= lastSupportRevision) {
				return false;
			}

			// ask for reviews only when a significant number of files have been processed
			if (sessionRenameCount < 5 || totalRenameCount < 5000) {
				return false;
			}

			// ask for reviews at most every once in a while
			if (Math.random() <= 0.777) {
				return false;
			}

			return super.feelingLucky(sessionRenameCount, totalRenameCount, currentRevision, lastSupportRevision, supportRevisionCount);
		}

		@Override
		String getURI() {
			return getAppStoreLink();
		}

	};

	public boolean feelingLucky(int sessionRenameCount, int totalRenameCount, int currentRevision, int lastSupportRevision, int supportRevisionCount) {
		// lucky if many files are processed in a single session
		if (sessionRenameCount >= 2000 * Math.pow(2, supportRevisionCount)) {
			return true;
		}

		// lucky if many many files have been processed over time
		if (totalRenameCount >= 2000 * Math.pow(5, supportRevisionCount)) {
			return true;
		}

		return false;
	}

	public boolean show(int totalRenameCount, boolean first) {
		String message = getMessage(totalRenameCount);
		String[] actions = getActions(first);
		JOptionPane pane = new JOptionPane(message, INFORMATION_MESSAGE, YES_NO_OPTION, getIcon(), actions, actions[0]);
		pane.createDialog(null, getTitle()).setVisible(true);

		// open URI of OK
		if (pane.getValue() == actions[0]) {
			openURI(getURI());
		}

		// don't ask again for this version regardless of user choice
		return true;
	}

	abstract String getMessage(int totalRenameCount);

	abstract String[] getActions(boolean first);

	abstract Icon getIcon();

	abstract String getTitle();

	abstract String getURI();

	public void maybeShow() {
		try {
			PreferencesEntry<String> persistentSupportRevision = Settings.forPackage(SupportDialog.class).entry("support.revision");
			List<Integer> supportRevision = matchIntegers(persistentSupportRevision.getValue());

			int lastSupportRevision = supportRevision.stream().max(Integer::compare).orElse(0);
			int currentRevision = getApplicationRevisionNumber();

			int sessionRenameCount = HistorySpooler.getInstance().getSessionHistoryTotalSize();
			int totalRenameCount = HistorySpooler.getInstance().getPersistentHistoryTotalSize();

			// show donation / review reminders to power users
			if (feelingLucky(sessionRenameCount, totalRenameCount, currentRevision, lastSupportRevision, supportRevision.size())) {
				if (show(totalRenameCount, supportRevision.isEmpty())) {
					supportRevision = Stream.concat(supportRevision.stream(), Stream.of(currentRevision)).sorted().distinct().collect(toList());
					persistentSupportRevision.setValue(supportRevision.toString());
				}
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e, e::toString);
		}
	}

}
