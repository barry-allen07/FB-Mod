package net.filebot.ui;

import java.awt.Color;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.filebot.util.ui.LinkButton;
import net.miginfocom.swing.MigLayout;

public class HistoryPanel extends JPanel {

	private final List<JLabel> columnHeaders = new ArrayList<JLabel>(3);

	public HistoryPanel() {
		super(new MigLayout("fillx, insets 10 30 10 50, wrap 3"));

		setBackground(Color.WHITE);
		setOpaque(true);

		setupHeader();
	}

	private void setupHeader() {
		for (int i = 0; i < 3; i++) {
			JLabel columnHeader = new JLabel();

			columnHeader.setFont(columnHeader.getFont().deriveFont(Font.BOLD));

			columnHeaders.add(columnHeader);

			add(columnHeader, getHeaderConstraint(i));
		}
	}

	private String getHeaderConstraint(int headerIndex) {
		switch (headerIndex) {
		case 0:
			return "align left, gapbefore 24";
		case 1:
			return "align center";
		default:
			return "align right, gapafter 12";
		}
	}

	public void setColumnHeader(int index, String text) {
		columnHeaders.get(index).setText(text);
	}

	public void add(String column1, URI link, Icon icon, String column2, String column3) {
		JComponent c1 = link != null ? new LinkButton(column1, null, icon, link) : new JLabel(column1, icon, SwingConstants.LEFT);
		JComponent c2 = new JLabel(column2, SwingConstants.RIGHT);
		JComponent c3 = new JLabel(column3, SwingConstants.RIGHT);

		add(c1, "align left");

		// set minimum with to 100px so the text is aligned to the right,
		// even though the whole label is centered
		add(c2, "align center, wmin 100");

		add(c3, "align right");
	}
}
