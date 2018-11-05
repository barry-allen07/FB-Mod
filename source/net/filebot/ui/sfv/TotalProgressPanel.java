
package net.filebot.ui.sfv;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

class TotalProgressPanel extends JComponent {

	private final JProgressBar progressBar = new JProgressBar(0, 0);

	private final int millisToSetVisible = 200;

	public TotalProgressPanel(ChecksumComputationService computationService) {
		setLayout(new MigLayout("insets 1px"));

		setBorder(new TitledBorder("Total Progress"));

		// invisible by default
		setVisible(false);

		progressBar.setStringPainted(true);

		add(progressBar, "growx");

		computationService.addPropertyChangeListener(progressListener);
	}

	private final PropertyChangeListener progressListener = new PropertyChangeListener() {

		private static final String SHOW = "show";
		private static final String HIDE = "hide";

		private final DelayedToggle delayed = new DelayedToggle();

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			int completedTaskCount = getComputationService(evt).getCompletedTaskCount();
			int totalTaskCount = getComputationService(evt).getTotalTaskCount();

			// invoke on EDT
			SwingUtilities.invokeLater(() -> {
				if (completedTaskCount == totalTaskCount) {
					// delayed hide on reset, immediate hide on finish
					delayed.toggle(HIDE, totalTaskCount == 0 ? millisToSetVisible : 0, visibilityActionHandler);
				} else if (totalTaskCount != 0) {
					delayed.toggle(SHOW, millisToSetVisible, visibilityActionHandler);
				}

				if (totalTaskCount != 0) {
					progressBar.setValue(completedTaskCount);
					progressBar.setMaximum(totalTaskCount);
					progressBar.setString(String.format("%d / %d", completedTaskCount, totalTaskCount));
				}
			});
		}

		private ChecksumComputationService getComputationService(PropertyChangeEvent evt) {
			return ((ChecksumComputationService) evt.getSource());
		}

		private final ActionListener visibilityActionHandler = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				setVisible(e.getActionCommand() == SHOW);
			}
		};

	};

	protected static class DelayedToggle {

		private Timer timer = null;

		public void toggle(String action, int delay, final ActionListener actionHandler) {
			if (timer != null) {
				if (action.equals(timer.getActionCommand())) {
					// action has not changed, don't stop existing timer
					return;
				}

				timer.stop();
			}

			timer = new Timer(delay, new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					actionHandler.actionPerformed(e);
				}
			});

			timer.setActionCommand(action);
			timer.setRepeats(false);
			timer.start();
		}

	}

}
