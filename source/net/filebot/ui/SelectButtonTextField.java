package net.filebot.ui;

import static javax.swing.BorderFactory.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;

import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.util.ui.SelectButton;
import net.filebot.util.ui.SwingUI;
import net.miginfocom.swing.MigLayout;

public class SelectButtonTextField<T> extends JComponent {

	private SelectButton<T> selectButton = new SelectButton<T>();

	private JComboBox<Object> editor = new JComboBox<Object>();

	public SelectButtonTextField() {
		selectButton.addActionListener(textFieldFocusOnClick);

		editor.setBorder(createMatteBorder(1, 0, 1, 1, ((LineBorder) selectButton.getBorder()).getLineColor()));

		setLayout(new MigLayout("fill, nogrid, novisualpadding"));
		add(selectButton, "h pref!, w pref!, sizegroupy editor");
		add(editor, "gap 0, w 195px!, sizegroupy editor");

		editor.setPrototypeDisplayValue("X");
		editor.setRenderer(new CompletionCellRenderer());
		editor.setUI(new TextFieldComboBoxUI(selectButton));
		editor.setMaximumRowCount(10);

		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(-1));
		SwingUI.installAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK), new SpinClientAction(1));
	}

	public String getText() {
		return ((TextFieldComboBoxUI) editor.getUI()).getEditor().getText();
	}

	public JComboBox getEditor() {
		return editor;
	}

	public SelectButton<T> getSelectButton() {
		return selectButton;
	}

	private final ActionListener textFieldFocusOnClick = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			getEditor().requestFocus();
		}

	};

	private class SpinClientAction extends AbstractAction {

		private int spin;

		public SpinClientAction(int spin) {
			super(String.format("Spin%+d", spin));
			this.spin = spin;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			selectButton.spinValue(spin);
		}
	}

	private class CompletionCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			setBorder(new EmptyBorder(1, 4, 1, 4));

			String highlightText = SelectButtonTextField.this.getText().substring(0, ((TextFieldComboBoxUI) editor.getUI()).getEditor().getSelectionStart());

			// highlight the matching sequence
			Matcher matcher = Pattern.compile(highlightText, Pattern.LITERAL | Pattern.CASE_INSENSITIVE).matcher(value.toString());

			// use no-break, because we really don't want line-wrapping in our table cells
			StringBuffer htmlText = new StringBuffer("<html><nobr>");
			if (matcher.find()) {
				if (isSelected) {
					matcher.appendReplacement(htmlText, "<span style='font-weight: bold;'>$0</span>");
				} else {
					matcher.appendReplacement(htmlText, "<span style='color: " + SwingUI.toHex(list.getSelectionBackground()) + "; font-weight: bold;'>$0</span>");
				}
			}
			matcher.appendTail(htmlText);
			htmlText.append("</nobr></html>");

			setText(htmlText.toString());
			return this;
		}
	}

	private static class TextFieldComboBoxUI extends BasicComboBoxUI {

		private SelectButton<?> button;

		public TextFieldComboBoxUI(SelectButton<?> button) {
			this.button = button;
		}

		@Override
		protected JButton createArrowButton() {
			return new JButton(ResourceManager.getIcon("action.list"));
		}

		@Override
		public void configureArrowButton() {
			super.configureArrowButton();

			arrowButton.setBackground(Color.white);
			arrowButton.setOpaque(true);
			arrowButton.setBorder(createEmptyBorder());
			arrowButton.setContentAreaFilled(false);
			arrowButton.setFocusPainted(false);
			arrowButton.setFocusable(false);

			// fix Aqua UI
			if (Settings.isMacApp()) {
				arrowButton.setContentAreaFilled(true);
			}
		}

		@Override
		protected void configureEditor() {
			JTextComponent editor = getEditor();

			editor.setEnabled(comboBox.isEnabled());
			editor.setFocusable(comboBox.isFocusable());
			editor.setFont(comboBox.getFont());
			editor.setBorder(createEmptyBorder(0, 3, 0, 3));

			editor.addFocusListener(createFocusListener());

			editor.getDocument().addDocumentListener(new DocumentListener() {

				@Override
				public void changedUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

				@Override
				public void insertUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					popup.getList().repaint();
				}

			});

			// massive performance boost for list rendering is cell height is fixed
			popup.getList().setPrototypeCellValue("X");
		}

		public JTextComponent getEditor() {
			return (JTextComponent) editor;
		}

		@Override
		protected ComboPopup createPopup() {
			return new BasicComboPopup(comboBox) {

				@Override
				public void show(Component invoker, int x, int y) {
					super.show(invoker, x - button.getWidth(), y);
				}

				@Override
				protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
					Rectangle bounds = super.computePopupBounds(px, py, pw, ph);
					bounds.width += button.getWidth();

					return bounds;
				}
			};
		}

		@Override
		protected FocusListener createFocusListener() {
			return new FocusHandler() {

				/**
				 * Prevent action events from being fired on focusLost.
				 */
				@Override
				public void focusLost(FocusEvent e) {
					if (isPopupVisible(comboBox)) {
						setPopupVisible(comboBox, false);
					}
				}
			};
		}

	}

}
