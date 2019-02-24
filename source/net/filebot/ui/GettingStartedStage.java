package net.filebot.ui;

import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import java.util.Locale;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GettingStartedStage {

	private Stage stage;

	public GettingStartedStage(Stage stage) {
		this.stage = stage;

		WebView webview = new WebView();
		webview.getEngine().load(getEmbeddedHelpURL());
		webview.setPrefSize(750, 490);

		// intercept target _blank click events and open links in a new browser window
		webview.getEngine().setCreatePopupHandler(c -> onPopup(webview));

		webview.getEngine().getLoadWorker().stateProperty().addListener((v, o, n) -> {
			if (n == Worker.State.SUCCEEDED) {
				stage.setTitle(webview.getEngine().getTitle());
				stage.toFront();
				webview.requestFocus();

				Timeline timeline = new Timeline(new KeyFrame(Duration.millis(750), new KeyValue(stage.opacityProperty(), 1.0, Interpolator.EASE_IN)));
				timeline.setOnFinished((evt) -> {
					stage.setOpacity(1.0);
					stage.requestFocus();
				});
				timeline.play();
			} else if (n == Worker.State.FAILED) {
				stage.close();
			}
		});

		stage.setTitle("Loading ...");
		stage.setScene(new Scene(webview, webview.getPrefWidth(), webview.getPrefHeight(), Color.BLACK));

		// make sure that we can read the user locale in JS
		webview.getEngine().executeScript(String.format("navigator.locale = '%s'", Locale.getDefault()));
	}

	public void show() {
		stage.setOpacity(0.0);
		stage.show();
	}

	protected WebEngine onPopup(WebView webview) {
		// get currently select image via Galleria API
		Object uri = webview.getEngine().executeScript("$('.galleria').data('galleria').getData().link");
		openURI(uri.toString());

		// prevent current web view from opening the link
		return null;
	}

}
