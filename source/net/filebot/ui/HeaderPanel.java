package net.filebot.ui;

import static javax.swing.BorderFactory.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.filebot.util.ui.notification.SeparatorBorder.Position;

public class HeaderPanel extends JComponent {

	private JLabel titleLabel = new JLabel();

	private float[] gradientFractions = { 0.0f, 0.5f, 1.0f };
	private Color[] gradientColors = { new Color(0xF6F6F6), new Color(0xF8F8F8), new Color(0xF3F3F3) };

	public HeaderPanel() {
		setLayout(new BorderLayout());
		setBackground(Color.WHITE);

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);

		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setVerticalAlignment(SwingConstants.CENTER);
		titleLabel.setOpaque(false);
		titleLabel.setForeground(new Color(0x101010));
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));

		centerPanel.setBorder(createEmptyBorder());
		centerPanel.add(titleLabel, BorderLayout.CENTER);

		add(centerPanel, BorderLayout.CENTER);

		setBorder(new SeparatorBorder(1, new Color(0xB4B4B4), new Color(0xACACAC), GradientStyle.LEFT_TO_RIGHT, Position.BOTTOM));
	}

	public void setTitle(String title) {
		titleLabel.setText(title);
	}

	public JLabel getTitleLabel() {
		return titleLabel;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		LinearGradientPaint paint = new LinearGradientPaint(0, 0, getWidth(), 0, gradientFractions, gradientColors);

		g2d.setPaint(paint);
		g2d.fill(getBounds());
	}

}
