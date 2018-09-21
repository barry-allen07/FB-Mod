
package net.filebot.ui.sfv;


import java.awt.Component;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.ResourceManager;
import net.filebot.ui.sfv.ChecksumRow.State;


class StateIconCellRenderer extends DefaultTableCellRenderer {

	private final Map<State, Icon> icons = new EnumMap<State, Icon>(State.class);


	public StateIconCellRenderer() {
		icons.put(State.UNKNOWN, ResourceManager.getIcon("status.unknown"));
		icons.put(State.OK, ResourceManager.getIcon("status.ok"));
		icons.put(State.WARNING, ResourceManager.getIcon("status.warning"));
		icons.put(State.ERROR, ResourceManager.getIcon("status.error"));

		setVerticalAlignment(SwingConstants.CENTER);
		setHorizontalAlignment(SwingConstants.CENTER);
	}


	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, null, isSelected, false, row, column);

		setIcon(icons.get(value));

		return this;
	}

}
