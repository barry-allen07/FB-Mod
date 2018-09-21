package net.filebot.ui.episodelist;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.List;

import javax.swing.JComponent;

import net.filebot.ui.FileBotList;
import net.filebot.ui.FileBotListExportHandler;
import net.filebot.ui.transfer.ArrayTransferable;
import net.filebot.ui.transfer.ClipboardHandler;
import net.filebot.ui.transfer.CompositeTranserable;
import net.filebot.util.StringUtilities;
import net.filebot.web.Episode;

class EpisodeListExportHandler extends FileBotListExportHandler<Episode> implements ClipboardHandler {

	public EpisodeListExportHandler(FileBotList<Episode> list) {
		super(list);
	}

	@Override
	public Transferable createTransferable(JComponent c) {
		Transferable episodeArray = export(list, true);
		Transferable textFile = super.createTransferable(c);

		return new CompositeTranserable(episodeArray, textFile);
	}

	@Override
	public void exportToClipboard(JComponent c, Clipboard clipboard, int action) throws IllegalStateException {
		ArrayTransferable<Episode> episodeData = export(list, false);
		StringSelection stringSelection = new StringSelection(StringUtilities.join(episodeData.getArray(), System.lineSeparator()));

		clipboard.setContents(new CompositeTranserable(episodeData, stringSelection), null);
	}

	public ArrayTransferable<Episode> export(FileBotList<?> list, boolean forceAll) {
		Episode[] selection = ((List<?>) list.getListComponent().getSelectedValuesList()).stream().map(Episode.class::cast).toArray(Episode[]::new);

		if (forceAll || selection.length == 0) {
			selection = list.getModel().stream().map(Episode.class::cast).toArray(Episode[]::new);
		}

		return new ArrayTransferable<Episode>(selection);
	}

}
