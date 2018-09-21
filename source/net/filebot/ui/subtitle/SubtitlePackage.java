package net.filebot.ui.subtitle;

import static java.util.Collections.*;
import static net.filebot.MediaTypes.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;
import javax.swing.event.SwingPropertyChangeSupport;

import net.filebot.Language;
import net.filebot.util.FileUtilities;
import net.filebot.vfs.ArchiveType;
import net.filebot.vfs.MemoryFile;
import net.filebot.web.SubtitleDescriptor;
import net.filebot.web.SubtitleProvider;

public class SubtitlePackage {

	private final SubtitleProvider provider;
	private final SubtitleDescriptor subtitle;
	private final Language language;

	private Download download;

	public SubtitlePackage(SubtitleProvider provider, SubtitleDescriptor subtitle) {
		this.provider = provider;
		this.subtitle = subtitle;

		// resolve language name
		this.language = Language.findLanguage(subtitle.getLanguageName());

		// initialize download worker
		download = new Download(subtitle);

		// forward phase events
		download.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().equals("phase")) {
					pcs.firePropertyChange("download.phase", evt.getOldValue(), evt.getNewValue());
				}
			}
		});
	}

	public SubtitleProvider getProvider() {
		return provider;
	}

	public String getName() {
		return subtitle.getName();
	}

	public Language getLanguage() {
		return language;
	}

	public String getType() {
		return subtitle.getType();
	}

	public Download getDownload() {
		return download;
	}

	public void reset() {
		// cancel old download
		download.cancel(false);

		// create new download
		Download old = download;
		download = new Download(subtitle);

		// transfer listeners
		for (PropertyChangeListener listener : old.getPropertyChangeSupport().getPropertyChangeListeners()) {
			old.removePropertyChangeListener(listener);
			download.addPropertyChangeListener(listener);
		}

		pcs.firePropertyChange("download.phase", old.getPhase(), download.getPhase());
	}

	@Override
	public String toString() {
		return subtitle.getName();
	}

	private final PropertyChangeSupport pcs = new SwingPropertyChangeSupport(this, true);

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public static class Download extends SwingWorker<List<MemoryFile>, Void> {

		enum Phase {
			PENDING, WAITING, DOWNLOADING, EXTRACTING, DONE
		}

		private final SubtitleDescriptor subtitle;

		private Phase current = Phase.PENDING;

		private Download(SubtitleDescriptor descriptor) {
			this.subtitle = descriptor;
		}

		public void start() {
			setPhase(Phase.WAITING);

			// enqueue worker
			execute();
		}

		@Override
		protected List<MemoryFile> doInBackground() throws Exception {
			setPhase(Phase.DOWNLOADING);

			// fetch archive
			ByteBuffer data = subtitle.fetch();

			// abort if download has been cancelled
			if (isCancelled())
				return null;

			setPhase(Phase.EXTRACTING);

			ArchiveType archiveType = ArchiveType.forName(subtitle.getType());

			if (archiveType == ArchiveType.UNKOWN) {
				// cannot extract files from archive
				return singletonList(new MemoryFile(subtitle.getPath(), data));
			}

			// extract contents of the archive
			List<MemoryFile> vfs = extract(archiveType, data);

			// if we can't extract files from a rar archive, it might actually be a zip file with the wrong extension
			if (vfs.isEmpty() && archiveType != ArchiveType.ZIP) {
				vfs = extract(ArchiveType.ZIP, data);
			}

			if (vfs.isEmpty()) {
				throw new IOException("Cannot extract files from archive");
			}

			// return file contents
			return vfs;
		}

		private List<MemoryFile> extract(ArchiveType archiveType, ByteBuffer data) throws IOException {
			List<MemoryFile> vfs = new ArrayList<MemoryFile>();

			for (MemoryFile file : archiveType.fromData(data)) {
				if (SUBTITLE_FILES.accept(file.getName())) {
					// add subtitle files, ignore non-subtitle files
					vfs.add(file);
				} else {
					// check if file is a supported archive
					ArchiveType type = ArchiveType.forName(FileUtilities.getExtension(file.getName()));

					if (type != ArchiveType.UNKOWN) {
						// extract nested archives recursively
						vfs.addAll(extract(type, file.getData()));
					}
				}
			}

			return vfs;
		}

		@Override
		protected void done() {
			setPhase(Phase.DONE);
		}

		private void setPhase(Phase phase) {
			Phase old = current;
			current = phase;

			firePropertyChange("phase", old, phase);
		}

		public boolean isStarted() {
			return current != Phase.PENDING;
		}

		public Phase getPhase() {
			return current;
		}
	}

}
