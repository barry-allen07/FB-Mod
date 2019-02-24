package net.filebot.ui;

import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import java.net.URL;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.filebot.ResourceManager;

public class GettingStartedUtil {

	public static void openGettingStarted(boolean webview) {
		invokeJavaFX(() -> {
			if (webview) {
				openEmbeddedGettingStartedPage();
			} else {
				askGettingStartedHelp(); // libjfxwebkit.dylib cannot be deployed on the MAS due to deprecated dependencies
			}
		});
	}

	private static void askGettingStartedHelp() {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle("FileBot");
		alert.setHeaderText("Hello! Do you need help Getting Started?");
		alert.setContentText("If you have never used FileBot before, please have a look at the video tutorials first.");

		if (isWindowsApp()) {
			Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
			stage.getIcons().setAll(ResourceManager.getApplicationIconResources().map(URL::toString).map(Image::new).toArray(Image[]::new));
		}

		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			openURI(getEmbeddedHelpURL());
		}
	}

	private static void openEmbeddedGettingStartedPage() {
		Stage stage = new Stage();
		stage.setResizable(true);

		if (isWindowsApp()) {
			stage.getIcons().setAll(ResourceManager.getApplicationIconResources().map(URL::toString).map(Image::new).toArray(Image[]::new));
			stage.initStyle(StageStyle.DECORATED);
			stage.initModality(Modality.NONE);
		} else {
			stage.initStyle(StageStyle.UTILITY);
			stage.initModality(Modality.NONE);
		}

		new GettingStartedStage(stage).show();
	}

}
