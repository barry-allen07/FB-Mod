
package net.filebot.ui;


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;


public class FileBotTab<T extends JComponent> extends JComponent {

	private final FileBotTabComponent tabComponent = new FileBotTabComponent();

	private final T component;


	public FileBotTab(T component) {
		this.component = component;

		tabComponent.getCloseButton().addActionListener(closeAction);

		setLayout(new BorderLayout());
		add(component, BorderLayout.CENTER);
	}


	public void addTo(JTabbedPane tabbedPane) {
		tabbedPane.addTab(this.getTitle(), this);
		tabbedPane.setTabComponentAt(tabbedPane.indexOfComponent(this), tabComponent);
	}


	public void close() {
		if (!isClosed()) {
			getTabbedPane().remove(this);
		}
	}


	public boolean isClosed() {
		JTabbedPane tabbedPane = getTabbedPane();

		if (tabbedPane == null)
			return true;

		return getTabbedPane().indexOfComponent(this) < 0;
	}


	private JTabbedPane getTabbedPane() {
		return (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
	}


	public T getComponent() {
		return component;
	}


	public FileBotTabComponent getTabComponent() {
		return tabComponent;
	}


	public void setTitle(String title) {
		tabComponent.setText(title);
	}


	public String getTitle() {
		return tabComponent.getText();
	}


	public void setIcon(Icon icon) {
		tabComponent.setIcon(icon);
	}


	public Icon getIcon() {
		return tabComponent.getIcon();
	}


	public void setLoading(boolean loading) {
		tabComponent.setLoading(loading);
	}

	private final ActionListener closeAction = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			close();
		}

	};

}
