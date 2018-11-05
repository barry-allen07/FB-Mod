
package net.filebot.ui.sfv;

import java.awt.Component;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.ui.sfv.ChecksumRow.State;

/**
 * DefaultTableCellRenderer with highlighting of text patterns.
 */
class HighlightPatternCellRenderer extends DefaultTableCellRenderer {

	private final Pattern pattern;

	public HighlightPatternCellRenderer(Pattern pattern) {
		this.pattern = pattern;
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, false, row, column);

		// check for error or warning
		boolean isError = (EnumSet.of(State.ERROR, State.WARNING).contains(table.getValueAt(row, 0)));

		// highlight patterns by using a smaller font-size and changing the font-color to a dark green
		// do not change the font-color if cell is selected, because that would look ugly (imagine green text on blue background ...)
		Matcher matcher = pattern.matcher(String.valueOf(value));

		// use no-break, because we really don't want line-wrapping in our table cells
		StringBuffer htmlText = new StringBuffer("<html><nobr>");
		while (matcher.find()) {
			matcher.appendReplacement(htmlText, createReplacement(isSelected ? null : isError ? "red" : "#009900", "smaller", isError ? "bold" : null));
		}
		matcher.appendTail(htmlText).append("</nobr></html>");

		setText(htmlText.toString());
		return this;
	}

	protected String createReplacement(String cssColor, String cssFontSize, String cssFontWeight) {
		// build replacement string like
		// e.g. <span style='font-size: smaller; color: #009900;'>$0</span>
		StringBuilder replacement = new StringBuilder(60);

		replacement.append("<span style='");

		if (cssColor != null) {
			replacement.append("color:").append(cssColor).append(';');
		}

		if (cssFontSize != null) {
			replacement.append("font-size:").append(cssFontSize).append(';');
		}

		if (cssFontWeight != null) {
			replacement.append("font-weight:").append(cssFontWeight).append(';');
		}

		return replacement.append("'>$0</span>").toString();
	}

}
