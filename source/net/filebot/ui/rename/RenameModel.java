package net.filebot.ui.rename;

import static java.util.Collections.*;
import static net.filebot.util.ExceptionUtilities.*;
import static net.filebot.util.FileUtilities.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.TransformedList;
import ca.odell.glazedlists.event.ListEvent;
import net.filebot.similarity.Match;
import net.filebot.util.ui.SwingUI;

public class RenameModel extends MatchModel<Object, File> {

	private final FormattedFutureEventList names = new FormattedFutureEventList(this.values());

	private final Map<Object, MatchFormatter> formatters = new LinkedHashMap<Object, MatchFormatter>();

	private final MatchFormatter defaultFormatter = new MatchFormatter() {

		@Override
		public boolean canFormat(Match<?, ?> match) {
			return true;
		}

		@Override
		public String preview(Match<?, ?> match) {
			return replacePathSeparators(String.valueOf(match.getValue())).trim(); // clean up path separators like / or \
		}

		@Override
		public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) {
			return preview(match);
		}
	};

	private boolean preserveExtension = true;

	public EventList<FormattedFuture> names() {
		return names;
	}

	public EventList<File> files() {
		return candidates();
	}

	public boolean preserveExtension() {
		return preserveExtension;
	}

	public void setPreserveExtension(boolean preserveExtension) {
		this.preserveExtension = preserveExtension;

		// update formatted names
		names.refresh();
	}

	public Map<File, File> getRenameMap() {
		Map<File, File> map = new LinkedHashMap<File, File>();

		for (int i = 0; i < names.size(); i++) {
			if (hasComplement(i)) {
				// make sure we're dealing with regular File objects form here on out
				File source = new File(files().get(i).getPath());

				FormattedFuture task = names.get(i);
				StringBuilder destination = new StringBuilder();

				// append formatted name, throw exception if not ready
				try {
					destination.append(task.get(0, TimeUnit.SECONDS));
				} catch (ExecutionException e) {
					throw new IllegalStateException(String.format("\"%s\" could not be formatted: %s.", task.preview(), e.getCause().getMessage()));
				} catch (TimeoutException e) {
					throw new IllegalStateException(String.format("\"%s\" has not been formatted yet. Applying custom formats may take a while.", task.preview()));
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

				// append extension, if desired
				if (preserveExtension) {
					String extension = getExtension(source);
					if (extension != null) {
						destination.append('.').append(extension.toLowerCase());
					}
				}

				// insert mapping
				if (map.put(source, new File(destination.toString())) != null) {
					throw new IllegalStateException("Duplicate source file: " + source.getName());
				}
			}
		}

		return map;
	}

	public void useFormatter(Object key, MatchFormatter formatter) {
		if (formatter != null) {
			formatters.put(key, formatter);
		} else {
			formatters.remove(key);
		}

		// reformat matches
		names.refresh();
	}

	private MatchFormatter getFormatter(Match<Object, File> match) {
		for (MatchFormatter formatter : formatters.values()) {
			if (formatter.canFormat(match)) {
				return formatter;
			}
		}

		return defaultFormatter;
	}

	public Map<File, Object> getMatchContext(Match<Object, File> match) {
		// incomplete matches have no context
		if (match.getValue() == null || match.getCandidate() == null) {
			return emptyMap();
		}

		// provide matches context on demand
		return new AbstractMap<File, Object>() {

			@Override
			public Set<Entry<File, Object>> entrySet() {
				Set<Entry<File, Object>> context = new LinkedHashSet<Entry<File, Object>>();
				for (Match<Object, File> it : matches()) {
					if (it.getValue() != null && it.getCandidate() != null) {
						context.add(new SimpleImmutableEntry<File, Object>(it.getCandidate(), it.getValue()));
					}
				}
				return context;
			}
		};
	}

	private class FormattedFutureEventList extends TransformedList<Object, FormattedFuture> {

		private final List<FormattedFuture> futures = new ArrayList<FormattedFuture>();

		private final Executor backgroundFormatter = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

		public FormattedFutureEventList(EventList<Object> source) {
			super(source);
			this.source.addListEventListener(this);
		}

		@Override
		public FormattedFuture get(int index) {
			return futures.get(index);
		}

		@Override
		protected boolean isWritable() {
			// can't write to source directly
			return false;
		}

		@Override
		public void add(int index, FormattedFuture value) {
			source.add(index, value.getMatch().getValue());
		}

		@Override
		public FormattedFuture set(int index, FormattedFuture value) {
			FormattedFuture obsolete = get(index);

			source.set(index, value.getMatch().getValue());

			return obsolete;
		}

		@Override
		public FormattedFuture remove(int index) {
			FormattedFuture obsolete = get(index);

			source.remove(index);

			return obsolete;
		}

		@Override
		public void listChanged(ListEvent<Object> listChanges) {
			updates.beginEvent(true);

			while (listChanges.next()) {
				int index = listChanges.getIndex();
				int type = listChanges.getType();

				if (type == ListEvent.INSERT || type == ListEvent.UPDATE) {
					Match<Object, File> match = getMatch(index);

					// create new future
					FormattedFuture future = new FormattedFuture(match, !preserveExtension, getFormatter(match), getMatchContext(match));

					// update data
					if (type == ListEvent.INSERT) {
						futures.add(index, future);
						updates.elementInserted(index, future);
					} else if (type == ListEvent.UPDATE) {
						// set new future, dispose old future
						FormattedFuture obsolete = futures.set(index, future);

						cancel(obsolete);

						// Don't update view immediately, to avoid irritating flickering,
						// caused by a rapid succession of change events.
						// The worker may only need a couple of milliseconds to complete,
						// so the view will be notified of the change soon enough.
						SwingUI.invokeLater(50, new Runnable() {

							@Override
							public void run() {
								// task has not been started, no change events have been sent as of yet,
								// fire change event now
								if (future.getState() == StateValue.PENDING) {
									future.firePropertyChange("state", null, StateValue.PENDING);
								}
							}
						});
					}

					// observe and enqueue worker task
					submit(future);
				} else if (type == ListEvent.DELETE) {
					// remove future from data and formatter queue
					FormattedFuture obsolete = futures.remove(index);
					cancel(obsolete);
					updates.elementDeleted(index, obsolete);
				}
			}

			updates.commitEvent();
		}

		public void refresh() {
			updates.beginEvent(true);

			for (int i = 0; i < size(); i++) {
				FormattedFuture obsolete = futures.get(i);
				Match<Object, File> match = obsolete.getMatch();
				FormattedFuture future = new FormattedFuture(match, !preserveExtension, getFormatter(match), getMatchContext(match));

				// replace and cancel old future
				cancel(futures.set(i, future));

				// submit new future
				submit(future);

				updates.elementUpdated(i, obsolete, future);
			}

			updates.commitEvent();
		}

		private void submit(FormattedFuture future) {
			// observe and enqueue worker task
			future.addPropertyChangeListener(futureListener);
			backgroundFormatter.execute(future);
		}

		private void cancel(FormattedFuture future) {
			// remove listener and cancel worker task
			future.removePropertyChangeListener(futureListener);
			future.cancel(true);
		}

		private final PropertyChangeListener futureListener = new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				int index = futures.indexOf(evt.getSource());

				// sanity check
				if (index >= 0 && index < size()) {
					FormattedFuture future = (FormattedFuture) evt.getSource();

					updates.beginEvent(true);
					updates.elementUpdated(index, future, future);
					updates.commitEvent();
				}
			}
		};
	}

	public static class FormattedFuture extends SwingWorker<String, Void> {

		private final Match<Object, File> match;
		private final boolean extension;
		private final Map<File, Object> context;

		private final MatchFormatter formatter;

		private FormattedFuture(Match<Object, File> match, boolean extension, MatchFormatter formatter, Map<File, Object> context) {
			this.match = match;
			this.extension = extension;
			this.formatter = formatter;
			this.context = context;
		}

		public boolean isComplexFormat() {
			return formatter instanceof ExpressionFormatter;
		}

		public Match<Object, File> getMatch() {
			return match;
		}

		public String preview() {
			return formatter.preview(match).trim();
		}

		@Override
		protected String doInBackground() throws Exception {
			return formatter.format(match, extension, context).trim();
		}

		@Override
		public String toString() {
			if (isDone()) {
				try {
					return get(0, TimeUnit.SECONDS);
				} catch (Throwable t) {
					// find the original exception
					if (t.getCause() != null && t instanceof ExecutionException) {
						t = t.getCause();
					}
					return String.format("[%s] %s", getMessage(t), preview());
				}
			}

			// use preview if we are not ready yet
			return preview();
		}
	}

}
