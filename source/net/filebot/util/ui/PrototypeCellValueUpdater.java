package net.filebot.util.ui;

import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public class PrototypeCellValueUpdater<T> implements ListDataListener {

	private int longestItemLength = -1;

	private JList<T> list;
	private T defaultValue;

	public PrototypeCellValueUpdater(JList<T> list, T defaultValue) {
		this.list = list;
		this.defaultValue = defaultValue;
	}

	@Override
	public void intervalRemoved(ListDataEvent evt) {
		// reset prototype value
		ListModel<T> m = (ListModel<T>) evt.getSource();
		if (m.getSize() == 0) {
			longestItemLength = -1;
			list.setPrototypeCellValue(null);
		}
	}

	@Override
	public void intervalAdded(ListDataEvent evt) {
		contentsChanged(evt);
	}

	@Override
	public void contentsChanged(ListDataEvent evt) {
		ListModel<T> m = (ListModel<T>) evt.getSource();
		for (int i = evt.getIndex0(); i <= evt.getIndex1() && i < m.getSize(); i++) {
			T item = m.getElementAt(i);
			int itemLength = item.toString().length();
			if (itemLength > longestItemLength) {
				// cell values will not be updated if the prototype object remains the same (even if the object has changed) so we need to reset it
				if (item == list.getPrototypeCellValue()) {
					list.setPrototypeCellValue(defaultValue);
				}

				longestItemLength = itemLength;
				list.setPrototypeCellValue(item);
			}
		}
	}

}
