
package net.filebot.ui.sfv;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.filebot.ResourceManager;
import net.filebot.ui.PanelBuilder;

public class SfvPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "SFV";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.sfv");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof SfvPanelBuilder;
	}

	@Override
	public JComponent create() {
		return new SfvPanel();
	}

}
