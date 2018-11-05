
package net.filebot.ui.episodelist;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.filebot.ResourceManager;
import net.filebot.ui.PanelBuilder;

public class EpisodeListPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Episodes";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.episodelist");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof EpisodeListPanelBuilder;
	}

	@Override
	public JComponent create() {
		return new EpisodeListPanel();
	}

}
