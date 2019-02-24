package net.filebot.ui;

import static net.filebot.Settings.*;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;

import net.filebot.util.ui.SwingEventBus;
import net.miginfocom.swing.MigLayout;

public class SinglePanelFrame extends JFrame {

	public SinglePanelFrame(PanelBuilder builder) {
		super(String.format("%s %s", getApplicationName(), builder.getName()));

		JComponent panel = builder.create();

		JComponent c = (JComponent) getContentPane();
		c.setLayout(new MigLayout("insets 0, nogrid, fill", "fill", "fill"));
		c.add(panel);

		HeaderPanel headerPanel = new HeaderPanel();
		headerPanel.getTitleLabel().setBorder(new EmptyBorder(8, 8, 8, 8));
		headerPanel.getTitleLabel().setIcon(builder.getIcon());
		headerPanel.getTitleLabel().setText(builder.getName());
		headerPanel.getTitleLabel().setIconTextGap(15);
		c.add(headerPanel, "growx, dock north");

		setSize(850, 600);
		setMinimumSize(new Dimension(800, 400));

		SwingEventBus.getInstance().register(panel);
	}

}
