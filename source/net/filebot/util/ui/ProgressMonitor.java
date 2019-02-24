package net.filebot.util.ui;

import static net.filebot.Logging.*;
import static net.filebot.util.ui.SwingUI.*;

import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.controlsfx.dialog.ProgressDialog;

import javafx.concurrent.Task;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProgressMonitor<T> {

	public static <T> FutureTask<T> runTask(String title, String header, ProgressWorker<T> worker) {
		initJavaFX();

		Task<T> task = new Task<T>() {

			@Override
			protected T call() throws Exception {
				return worker.call(this::updateMessage, this::updateProgress, this::isCancelled);
			}
		};

		// show progress monitor if operation takes more than 2 seconds
		invokeJavaFX(() -> {
			try {
				ProgressDialog dialog = new ProgressDialog(task);
				dialog.initModality(Modality.APPLICATION_MODAL);
				dialog.setTitle(title);
				dialog.setHeaderText(header);

				Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
				stage.setAlwaysOnTop(true);
				stage.setOnCloseRequest(evt -> task.cancel());
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
			}
		});

		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();

		return task;
	}

	@FunctionalInterface
	public interface ProgressWorker<T> {
		T call(Consumer<String> message, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception;
	}

	private ProgressMonitor() {
		throw new UnsupportedOperationException();
	}

}
