package net.filebot.platform.mac;

import static java.util.Collections.*;
import static javax.swing.BorderFactory.*;
import static net.filebot.Logging.*;
import static net.filebot.UserFiles.*;
import static net.filebot.platform.mac.MacAppUtilities.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import net.filebot.ResourceManager;
import net.filebot.Settings;
import net.filebot.media.MediaDetection;
import net.filebot.ui.HeaderPanel;
import net.filebot.ui.transfer.DefaultTransferHandler;
import net.filebot.ui.transfer.FileTransferable;
import net.filebot.ui.transfer.TransferablePolicy;
import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.filebot.util.ui.notification.SeparatorBorder.Position;
import net.miginfocom.swing.MigLayout;

public class DropToUnlock extends JList<File> {

	public static final Map<String, String> persistentSecurityScopedBookmarks = Settings.forPackage(DropToUnlock.class).node("SecurityScopedBookmarks").asMap();

	public static void unlockBySecurityScopedBookmarks(List<File> folders) {
		synchronized (persistentSecurityScopedBookmarks) {
			Set<String> bookmarks = persistentSecurityScopedBookmarks.keySet();
			for (File folder : folders) {
				Optional<File> bookmarkForFolder = listPath(folder).stream().filter(f -> bookmarks.contains(f.getPath())).findFirst();
				if (bookmarkForFolder.isPresent() && isLockedFolder(folder)) {
					try {
						NSURL_URLByResolvingBookmarkData_startAccessingSecurityScopedResource(persistentSecurityScopedBookmarks.get(bookmarkForFolder.get().getPath()));
					} catch (Throwable e) {
						debug.severe("NSURL.URLByResolvingBookmarkData.startAccessingSecurityScopedResource: " + e);
					}
				}
			}
		}
	}

	public static void storeSecurityScopedBookmarks(List<File> folders) {
		synchronized (persistentSecurityScopedBookmarks) {
			Set<String> bookmarks = persistentSecurityScopedBookmarks.keySet();
			for (File folder : folders) {
				// check if folder (or one of it's parent folders) is already bookmarked
				if (disjoint(bookmarks, listPath(folder)) && !isLockedFolder(folder)) {
					try {
						String bookmarkData = NSURL_bookmarkDataWithOptions(folder.getPath());
						persistentSecurityScopedBookmarks.put(folder.getPath(), bookmarkData);
					} catch (Throwable e) {
						debug.severe("NSURL.bookmarkDataWithOptions: " + e);
					}
				}
			}
		}
	}

	// NOTE: The app sandbox seems to allow read access to /Volumes by default
	private static final File VOLUMES_FOLDER = new File("/Volumes");

	public static List<File> getParentFolders(Collection<File> files) {
		return files.stream().map(f -> f.isDirectory() ? f : f.getParentFile()).sorted().distinct().filter(f -> !f.exists() || isLockedFolder(f)).map(f -> {
			try {
				File file = f.getCanonicalFile();
				File root = MediaDetection.getStructureRoot(file);

				if (VOLUMES_FOLDER.equals(root)) {
					List<File> path = listPath(file);

					// i.e. [0] / [1] Volumes / [2] HDD
					if (path.size() >= 3) {
						return listPath(file).get(2);
					}
				}

				// if structure root doesn't work just grab first existing parent folder
				if (root == null || root.getName().isEmpty() || root.getParentFile() == null || root.getParentFile().getName().isEmpty()) {
					for (File it : listPathTail(file, Integer.MAX_VALUE, true)) {
						if (it.isDirectory()) {
							return it;
						}
					}
				}
				return root;
			} catch (Exception e) {
				debug.log(Level.WARNING, e, e::toString);
				return null;
			}
		}).filter(f -> f != null && !f.getName().isEmpty() && isLockedFolder(f)).sorted().distinct().collect(Collectors.toList());
	}

	public static boolean showUnlockFoldersDialog(Window owner, Collection<File> files) {
		List<File> model = getParentFolders(files);

		// immediately return if there is nothing that needs to be unlocked
		if (model.isEmpty()) {
			return true;
		}

		// try to restore permissions from previously stored security-scoped bookmarks as best as possible
		unlockBySecurityScopedBookmarks(model);

		// check if we even need to unlock anything at this point
		if (model.stream().allMatch(f -> !isLockedFolder(f))) {
			return true;
		}

		// show selection dialog on EDT
		RunnableFuture<Boolean> showPermissionDialog = new FutureTask<Boolean>(() -> {
			JDialog dialog = new JDialog(owner);
			AtomicBoolean dialogCancelled = new AtomicBoolean(true);

			DropToUnlock d = new DropToUnlock(model) {

				@Override
				public void updateLockStatus(File... folders) {
					super.updateLockStatus(folders);

					// if all folders have been unlocked auto-close dialog
					if (model.stream().allMatch(f -> !isLockedFolder(f))) {
						dialogCancelled.set(false);
						invokeLater(750, () -> dialog.setVisible(false)); // auto-close unlock dialog once all folders have been unlocked
						invokeLater(1000, () -> Desktop.getDesktop().requestForeground(true)); // bring application to foreground now that folders have been unlocked
					} else {
						model.stream().filter(f -> isLockedFolder(f)).findFirst().ifPresent(f -> {
							invokeLater(250, () -> {
								revealFiles(singleton(f));
							});
						});
					}
				};
			};
			d.setBorder(createEmptyBorder(5, 15, 120, 15));

			JComponent c = (JComponent) dialog.getContentPane();
			c.setLayout(new MigLayout("insets 0, fill"));

			HeaderPanel h = new HeaderPanel();
			h.getTitleLabel().setText("Folder Permissions Required");
			h.getTitleLabel().setIcon(ResourceManager.getIcon("file.lock"));
			h.getTitleLabel().setBorder(createEmptyBorder(0, 0, 0, 64));

			JLabel help = new JLabel("<html>FileBot does not have permission to access the folder above. To allow FileBot access, drag and drop the folder from Finder onto the drop area above</b>. The permissions for this folder (and all the folders it contains) will be remembered and FileBot will not need to ask for it again.</html>");
			help.setBorder(createCompoundBorder(new SeparatorBorder(1, new Color(0xB4B4B4), new Color(0xACACAC), GradientStyle.LEFT_TO_RIGHT, Position.TOP), createTitledBorder("About App Sandboxing")));

			c.add(h, "wmin 150px, hmin 75px, growx, dock north");
			c.add(d, "wmin 150px, hmin 150px, grow");
			c.add(help, "wmin 150px, hmin 75px, growx, aligny center, dock south");

			dialog.setModal(true);
			dialog.setModalExclusionType(ModalExclusionType.TOOLKIT_EXCLUDE);
			dialog.setSize(new Dimension(540, 500));
			dialog.setResizable(false);
			dialog.setLocationByPlatform(true);
			dialog.setAlwaysOnTop(true);

			// open required folders for easy drag and drop (a few milliseconds after the dialog has become visible)
			invokeLater(500, () -> {
				revealFiles(model);
			});

			// show and wait for user input
			dialog.setVisible(true);

			// abort if user has closed the window before all folders have been unlocked
			return !dialogCancelled.get();
		});

		// show dialog on EDT and wait for user input
		try {
			if (SwingUtilities.isEventDispatchThread()) {
				showPermissionDialog.run();
			} else {
				SwingUtilities.invokeAndWait(showPermissionDialog);
			}

			// store security-scoped bookmark if dialog was accepted
			if (showPermissionDialog.get()) {
				storeSecurityScopedBookmarks(model);
				return true;
			}
			return false;
		} catch (InterruptedException | InvocationTargetException | ExecutionException e) {
			throw new RuntimeException("Failed to request permissions: " + e.getMessage(), e);
		}
	}

	public DropToUnlock(Collection<File> model) {
		super(model.toArray(new File[0]));

		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(-1);

		setCellRenderer(new FolderLockCellRenderer());

		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new FileChooserAction());

		setTransferHandler(new DefaultTransferHandler(new FolderDropPolicy(), null));
	}

	public void updateLockStatus(File... folder) {
		// update folder locked/unlocked icon
		repaint();

		// show warning if permission have not been granted
		Stream.of(folder).filter(f -> isLockedFolder(f)).forEach(f -> {
			try {
				String owner = Files.getOwner(f.toPath()).getName();
				String permissions = PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath()));
				log.log(Level.SEVERE, format("Permission denied: %s (%s %s)", f, permissions, owner));
			} catch (Exception e) {
				log.log(Level.SEVERE, e, format("Permission denied: %s", f));
			}
		});
	}

	private final RoundRectangle2D dropArea = new RoundRectangle2D.Double(0, 0, 0, 0, 20, 20);
	private final BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[] { 5.0f }, 0.0f);

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// draw dashed bounding box
		int w = 300;
		int h = 70;
		int pad = 20;

		g2d.setColor(Color.lightGray);
		dropArea.setFrameFromCenter(getWidth() / 2, getHeight() - (h / 2) - pad - 10, (getWidth() - w) / 2, getHeight() - h - 2 * pad);
		g2d.setStroke(dashedStroke);
		g2d.draw(dropArea);

		// draw text
		g2d.setColor(Color.gray);
		g2d.setFont(g2d.getFont().deriveFont(Font.ITALIC, 36));
		g2d.drawString("Drop 'em", (int) dropArea.getMinX() + 15, (int) dropArea.getMinY() + 40);
		g2d.drawString("to Unlock 'em", (int) dropArea.getMinX() + 45, (int) dropArea.getMinY() + 40 + 35);
	}

	protected class FolderDropPolicy extends TransferablePolicy {

		@Override
		public boolean accept(Transferable tr) throws Exception {
			return true;
		}

		@Override
		public void handleTransferable(Transferable tr, TransferAction action) throws Exception {
			List<File> files = FileTransferable.getFilesFromTransferable(tr);
			if (files != null) {
				List<File> folders = filter(files, FOLDERS);
				if (folders.size() > 0) {
					updateLockStatus(folders.toArray(new File[0]));
				}
			}
		}
	}

	protected static class FolderLockCellRenderer extends DefaultListCellRenderer {

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(100, 100);
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			File folder = (File) value;
			JLabel c = (JLabel) super.getListCellRendererComponent(list, folder.getName(), index, false, false);

			c.setIcon(ResourceManager.getIcon(isLockedFolder(folder) ? "folder.locked" : "folder.open"));
			c.setHorizontalTextPosition(JLabel.CENTER);
			c.setVerticalTextPosition(JLabel.BOTTOM);
			c.setToolTipText(folder.getAbsolutePath());

			return c;
		}
	}

	protected static class FileChooserAction extends MouseAdapter {

		@Override
		public void mouseClicked(MouseEvent evt) {
			DropToUnlock list = (DropToUnlock) evt.getSource();
			if (evt.getClickCount() > 0) {
				int index = list.locationToIndex(evt.getPoint());
				if (index >= 0 && list.getCellBounds(index, index).contains(evt.getPoint())) {
					File folder = list.getModel().getElementAt(index);
					if (isLockedFolder(folder)) {
						if (null != showOpenDialogSelectFolder(folder, "Grant Permission", new ActionEvent(list, ActionEvent.ACTION_PERFORMED, "Grant"))) {
							list.updateLockStatus(folder);
						}
					}
				}
			}
		}
	}

}
