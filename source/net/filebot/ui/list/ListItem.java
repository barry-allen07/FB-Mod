package net.filebot.ui.list;

import net.filebot.format.ExpressionFormat;

public class ListItem {

	private IndexedBindingBean bindings;
	private ExpressionFormat format;

	private Object value;

	public ListItem(IndexedBindingBean bindings, ExpressionFormat format) {
		this.bindings = bindings;
		this.format = format;
		this.value = format != null ? null : bindings.getInfoObject().toString();
	}

	public IndexedBindingBean getBindings() {
		return bindings;
	}

	public Object getObject() {
		return bindings.getInfoObject();
	}

	public Object getFormattedValue() {
		if (value == null) {
			try {
				value = format.format(bindings);
			} catch (Exception e) {
				value = e;
			}
		}
		return value;
	}

	@Override
	public String toString() {
		return getObject().toString();
	}

}
