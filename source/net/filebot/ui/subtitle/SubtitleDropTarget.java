package net.filebot.ui.subtitle;

import static net.filebot.Logging.*;
import static net.filebot.MediaTypes.*;
import static net.filebot.Settings.*;
import static net.filebot.UserFiles.*;
import static net.filebot.ui.transfer.FileTransferable.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import net.filebot.ResourceManager;
import net.filebot.platform.mac.MacAppUtilities;
import net.filebot.ui.subtitle.upload.SubtitleUploadDialog;
import net.filebot.util.FileUtilities;
import net.filebot.util.FileUtilities.ExtensionFileFilter;
import net.filebot.util.FileUtilities.ParentFilter;
import net.filebot.web.OpenSubtitlesClient;
import net.filebot.web.SubtitleProvider;
import net.filebot.web.VideoHashSubtitleService;

abstract class SubtitleDropTarget extends JButton {

	public enum DropAction {
		Accept, Cancel
	}

	public SubtitleDropTarget() {
		setHorizontalAlignment(CENTER);

		setHideActionText(true);
		setContentAreaFilled(false);
		setFocusPainted(false);
		setBorderPainted(false);
		setBackground(Color.white);

		// initialize with default mode
		setDropAction(DropAction.Accept);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// install mouse listener
		addActionListener(clickHandler);

		// install drop target
		new DropTarget(this, dropHandler);
	}

	private Color lineColor = new Color(0xD7D7D7);

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		float w = getWidth();
		float h = getHeight();
		float d = Math.min(w, h) - 1f;

		Shape shape = new Ellipse2D.Float((w - d) / 2, (h - d) / 2, d - 1, d - 1);

		g2d.setColor(getBackground());
		g2d.fill(shape);

		g2d.setColor(lineColor);
		g2d.draw(shape);

		super.paintComponent(g2d);
	}

	protected void setDropAction(DropAction dropAction) {
		setIcon(getIcon(dropAction));
	}

	protected abstract OpenSubtitlesClient getSubtitleService();

	protected abstract boolean handleDrop(List<File> files);

	protected abstract DropAction getDropAction(List<File> files);

	protected abstract Icon getIcon(DropAction dropAction);

	private final DropTargetAdapter dropHandler = new DropTargetAdapter() {

		@Override
		public void dragEnter(DropTargetDragEvent dtde) {
			DropAction dropAction = DropAction.Accept;

			try {
				dropAction = getDropAction(getFilesFromTransferable(dtde.getTransferable()));
			} catch (Exception e) {
				// just accept the drag if we can't access the transferable,
				// because on some implementations we can't access transferable data before we accept the drag,
				// but accepting or rejecting the drag depends on the files dragged
			}

			// update visual representation
			setDropAction(dropAction);

			// accept or reject
			if (dropAction != DropAction.Cancel) {
				dtde.acceptDrag(DnDConstants.ACTION_REFERENCE);
			} else {
				dtde.rejectDrag();
			}
		}

		@Override
		public void dragExit(DropTargetEvent dte) {
			// reset to default state
			setDropAction(DropAction.Accept);
		};

		@Override
		public void drop(DropTargetDropEvent dtde) {
			dtde.acceptDrop(DnDConstants.ACTION_REFERENCE);
			boolean accept = false;

			try {
				List<File> files = getFilesFromTransferable(dtde.getTransferable());
				accept = getDropAction(files) != DropAction.Cancel;

				if (accept) {
					// invoke later so we don't block the DnD operation with the download dialog
					SwingUtilities.invokeLater(() -> handleDrop(files));
				}
			} catch (Exception e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}

			dtde.dropComplete(accept);
			dragExit(dtde);
		}

	};

	private final ActionListener clickHandler = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent evt) {
			// collect media file extensions (video and subtitle files)
			List<File> files = showLoadDialogSelectFiles(true, true, null, ExtensionFileFilter.union(VIDEO_FILES, SUBTITLE_FILES), "Select Video Folder", evt);

			if (files.size() > 0 && getDropAction(files) != DropAction.Cancel) {
				handleDrop(files);
			}
		}
	};

	public static abstract class Download extends SubtitleDropTarget {

		public abstract VideoHashSubtitleService[] getVideoHashSubtitleServices();

		public abstract SubtitleProvider[] getSubtitleProviders();

		public abstract Locale getQueryLanguage();

		@Override
		protected DropAction getDropAction(List<File> selection) {
			// accept video files and folders
			return filter(selection, VIDEO_FILES, FOLDERS).size() > 0 ? DropAction.Accept : DropAction.Cancel;
		}

		@Override
		protected boolean handleDrop(List<File> selection) {
			if (getQueryLanguage() == null) {
				log.info("Please select your preferred subtitle language.");
				return false;
			}

			List<File> files = listFiles(selection, VIDEO_FILES, HUMAN_NAME_ORDER);

			if (files.size() > 0) {
				handleDownload(files);
				return true;
			}

			return false;
		}

		protected boolean handleDownload(Collection<File> videoFiles) {
			SubtitleAutoMatchDialog dialog = new SubtitleAutoMatchDialog(getWindow(this));

			// initialize download parameters
			dialog.setVideoFiles(videoFiles.toArray(new File[0]));

			for (VideoHashSubtitleService service : getVideoHashSubtitleServices()) {
				dialog.addSubtitleService(service);
			}

			for (SubtitleProvider service : getSubtitleProviders()) {
				dialog.addSubtitleService(service);
			}

			// start looking for subtitles
			dialog.startQuery(getQueryLanguage());

			// initialize window properties
			dialog.setIconImage(getImage(getIcon(DropAction.Accept)));
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setSize(1050, 600);

			// show dialog
			dialog.setLocationRelativeTo(dialog.getOwner());
			dialog.setVisible(true);

			return true;
		}

		@Override
		protected Icon getIcon(DropAction dropAction) {
			switch (dropAction) {
			case Accept:
				return ResourceManager.getIcon("subtitle.exact.download");
			default:
				return ResourceManager.getIcon("message.error");
			}
		}

	}

	public static abstract class Upload extends SubtitleDropTarget {

		@Override
		protected DropAction getDropAction(List<File> selection) {
			// accept video files and folders
			return filter(selection, SUBTITLE_FILES).size() > 0 || filter(selection, FOLDERS).size() > 0 ? DropAction.Accept : DropAction.Cancel;
		}

		@Override
		protected boolean handleDrop(List<File> selection) {
			if (getSubtitleService().isAnonymous()) {
				log.info(String.format("%s: You must be logged in to upload subtitles.", getSubtitleService().getName()));
				return false;
			}

			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

			// make sure we have access to the parent folder structure, not just the dropped file
			if (isMacSandbox()) {
				MacAppUtilities.askUnlockFolders(getWindow(this), selection);
			}

			// perform a drop action depending on the given files
			List<File> files = listFiles(selection, FILES, HUMAN_NAME_ORDER);

			List<File> videos = filter(files, VIDEO_FILES);
			List<File> subtitles = filter(files, SUBTITLE_FILES);

			Map<File, File> uploadPlan = new LinkedHashMap<File, File>();

			for (File subtitle : subtitles) {
				File video = getVideoForSubtitle(subtitle, filter(videos, new ParentFilter(subtitle.getParentFile())));
				uploadPlan.put(subtitle, video);
			}

			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

			if (uploadPlan.size() > 0) {
				handleUpload(uploadPlan);
				return true;
			}

			return false;
		}

		protected void handleUpload(Map<File, File> uploadPlan) {
			SubtitleUploadDialog dialog = new SubtitleUploadDialog(getSubtitleService(), getWindow(this));

			// initialize window properties
			dialog.setIconImage(getImage(getIcon(DropAction.Accept)));
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setSize(950, 575);

			// show dialog
			dialog.setLocation(getOffsetLocation(dialog.getOwner()));

			// start processing
			dialog.setUploadPlan(uploadPlan);
			dialog.startChecking();

			// show dialog
			dialog.setVisible(true);
		}

		protected File getVideoForSubtitle(File subtitle, List<File> videos) {
			// 1. try to find exact match in drop data
			return findMatch(subtitle, videos, FileUtilities::getName).orElseGet(() -> {
				// 2. guess movie file from the parent folder if only a subtitle file was dropped in
				return findMatch(subtitle, getChildren(subtitle.getParentFile(), VIDEO_FILES), FileUtilities::getName).orElse(null);
			});
		}

		private Optional<File> findMatch(File file, List<File> options, Function<File, String> comparator) {
			String subtitleFileName = comparator.apply(file).toLowerCase();
			for (File it : options) {
				if (subtitleFileName.length() > 0 && subtitleFileName.startsWith(comparator.apply(it).toLowerCase())) {
					return Optional.of(it);
				}
			}
			return Optional.empty();
		}

		@Override
		protected Icon getIcon(DropAction dropAction) {
			if (dropAction == DropAction.Accept)
				return ResourceManager.getIcon("subtitle.exact.upload");

			return ResourceManager.getIcon("message.error");
		}
	}

}
