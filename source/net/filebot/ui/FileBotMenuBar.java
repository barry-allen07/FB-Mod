package net.filebot.ui;

import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

public class FileBotMenuBar {

	public static JMenuBar createHelp() {
		JMenu help = new JMenu("Help");

		help.add(createLink("Getting Started", getApplicationProperty("link.intro")));
		help.add(createLink("FAQ", getApplicationProperty("link.faq")));
		help.add(createLink("Forums", getApplicationProperty("link.forums")));
		help.add(createLink("Discord Channel", getApplicationProperty("link.channel")));

		help.addSeparator();

		if (isMacSandbox()) {
			help.add(createLink("Report Bugs", getApplicationProperty("link.help.mas")));
			help.add(createLink("Request Help", getApplicationProperty("link.help.mas")));
		} else {
			help.add(createLink("Report Bugs", getApplicationProperty("link.bugs")));
			help.add(createLink("Request Help", getApplicationProperty("link.help")));
		}

		help.addSeparator();

		help.add(createLink("Contact us on Twitter", getApplicationProperty("link.twitter")));
		help.add(createLink("Contact us on Facebook", getApplicationProperty("link.facebook")));

		JMenuBar menuBar = new JMenuBar();
		menuBar.add(help);
		return menuBar;
	}

	private static Action createLink(final String title, final String uri) {
		return newAction(title, null, evt -> openURI(uri));
	}

}
