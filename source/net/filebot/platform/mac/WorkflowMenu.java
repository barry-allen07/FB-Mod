package net.filebot.platform.mac;

import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.StandardFileAction.Verb.*;

import java.io.File;
import java.io.FileFilter;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.util.ui.SwingUI.DynamicMenu;
import net.filebot.util.ui.SwingUI.StandardFileAction;

public class WorkflowMenu extends DynamicMenu {

	public static final FileFilter WORKFLOW = new ExtensionFileFilter("workflow");

	public File templateFolder;
	public File targetFolder;

	public WorkflowMenu(String label, File templateFolder, File targetFolder) {
		super(label);

		this.templateFolder = templateFolder;
		this.targetFolder = targetFolder;
	}

	@Override
	protected void populate() {
		for (File template : templateFolder.listFiles(WORKFLOW)) {
			File target = new File(targetFolder, template.getName());

			if (target.exists()) {
				add(createUninstallMenu(target));
			} else {
				add(createInstallMenu(template));
			}
		}
	}

	protected JMenuItem createInstallMenu(File workflow) {
		JMenu menu = new JMenu(getNameWithoutExtension(workflow.getName()));

		menu.add(new StandardFileAction("Install", workflow, OPEN));
		menu.add(new StandardFileAction("Reveal", workflow, REVEAL));

		return menu;
	}

	protected JMenuItem createUninstallMenu(File workflow) {
		JMenu menu = new JMenu(getNameWithoutExtension(workflow.getName()));

		menu.add(new StandardFileAction("Uninstall", workflow, TRASH));
		menu.add(new StandardFileAction("Open", workflow, OPEN));
		menu.add(new StandardFileAction("Reveal", workflow, REVEAL));

		return menu;
	}

}
