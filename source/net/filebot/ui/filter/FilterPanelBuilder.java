
package net.filebot.ui.filter;

import javax.swing.Icon;
import javax.swing.JComponent;

import net.filebot.ResourceManager;
import net.filebot.ui.PanelBuilder;

public class FilterPanelBuilder implements PanelBuilder {

	@Override
	public String getName() {
		return "Filter";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("panel.analyze");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FilterPanelBuilder;
	}

	@Override
	public JComponent create() {
		FilterPanel panel = new FilterPanel();
		panel.addTool(new ExtractTool());
		panel.addTool(new TypeTool());
		panel.addTool(new SplitTool());
		panel.addTool(new AttributeTool());
		panel.addTool(new MediaInfoTool());
		return panel;
	}

}
