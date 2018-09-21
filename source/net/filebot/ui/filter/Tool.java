package net.filebot.ui.filter;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.ExceptionUtilities.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import javax.swing.JComponent;
import javax.swing.SwingWorker;
import javax.swing.tree.TreeNode;

import net.filebot.ui.filter.FileTree.FileNode;
import net.filebot.ui.filter.FileTree.FolderNode;
import net.filebot.util.FileUtilities;
import net.filebot.util.ui.LoadingOverlayPane;

abstract class Tool<M> extends JComponent {

	private List<File> root = emptyList();

	private UpdateModelTask updateTask;

	public Tool(String name) {
		setName(name);
	}

	public List<File> getRoot() {
		return root;
	}

	public void setRoot(List<File> root) {
		this.root = root;

		if (updateTask != null) {
			updateTask.cancel(true);
		}

		setLoading(true);

		updateTask = new UpdateModelTask(root);
		updateTask.execute();
	}

	protected void setLoading(boolean loading) {
		firePropertyChange(LoadingOverlayPane.LOADING_PROPERTY, !loading, loading);
	}

	protected abstract M createModelInBackground(List<File> root) throws Exception;

	protected abstract void setModel(M model);

	private class UpdateModelTask extends SwingWorker<M, Void> {

		private final List<File> root;

		public UpdateModelTask(List<File> root) {
			this.root = root;
		}

		@Override
		protected M doInBackground() throws Exception {
			return createModelInBackground(root);
		}

		@Override
		protected void done() {
			if (this == updateTask) {
				setLoading(false);
			}

			// update task will only be cancelled if a newer update task has been started
			if (this == updateTask && !isCancelled()) {
				try {
					setModel(get());
				} catch (Exception e) {
					Throwable cause = getRootCause(e);

					if (cause instanceof InterruptedException || cause instanceof CancellationException) {
						debug.log(Level.FINEST, e, e::toString); // if it happens, it is supposed to
					} else {
						debug.log(Level.WARNING, e, e::toString); // should not happen
					}
				}
			}
		}
	}

	protected List<TreeNode> createFileNodes(Collection<File> files) {
		return files.stream().map(FileNode::new).collect(toList());
	}

	protected FolderNode createStatisticsNode(String name, List<File> files) {
		List<File> selection = listFiles(files, FILES, null);
		long size = selection.stream().mapToLong(File::length).sum();

		// set node text (e.g. txt (1 file, 42 Byte))
		String title = String.format("%s (%,d %s, %s)", name, selection.size(), selection.size() == 1 ? "file" : "files", FileUtilities.formatSize(size));

		return new FolderNode(title, createFileNodes(files));
	}

}
