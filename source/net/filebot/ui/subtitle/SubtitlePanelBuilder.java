
package net.filebot.ui.subtitle;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.filebot.ResourceManager;
import net.filebot.ui.PanelBuilder;

public class SubtitlePanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Subtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.subtitle");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SubtitlePanelBuilder;
	}

	@Override
	public JComponent create() {
		return new SubtitlePanel();
	}

}
