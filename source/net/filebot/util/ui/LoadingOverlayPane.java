
package net.filebot.util.ui;

import javax.swing.JComponent;

import net.miginfocom.swing.MigLayout;

public class LoadingOverlayPane extends JComponent {

	public static final String LOADING_PROPERTY = "loading";

	private final JComponent animationComponent;

	private boolean overlayEnabled = false;

	private int millisToOverlay = 400;

	public LoadingOverlayPane(JComponent component, JComponent propertyChangeSource) {
		this(component, propertyChangeSource, null, null);
	}

	public LoadingOverlayPane(JComponent component, JComponent propertyChangeSource, String offsetX, String offsetY) {
		setLayout(new MigLayout("insets 0, fill"));

		animationComponent = new ProgressIndicator();
		animationComponent.setVisible(false);

		add(animationComponent, String.format("pos n %s 100%%-%s n", offsetY != null ? offsetY : "8px", offsetX != null ? offsetX : "20px"));
		add(component, "grow");

		if (propertyChangeSource != null) {
			propertyChangeSource.addPropertyChangeListener(LOADING_PROPERTY, evt -> {
				setOverlayVisible((Boolean) evt.getNewValue());
			});
		}
	}

	@Override
	public boolean isOptimizedDrawingEnabled() {
		return false;
	}

	public void setOverlayVisible(boolean b) {
		overlayEnabled = b;

		if (overlayEnabled) {
			SwingUI.invokeLater(millisToOverlay, new Runnable() {

				@Override
				public void run() {
					if (overlayEnabled) {
						animationComponent.setVisible(true);
					}
				}

			});
		} else {
			animationComponent.setVisible(false);
		}
	}

}
