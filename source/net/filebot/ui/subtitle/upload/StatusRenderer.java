package net.filebot.ui.subtitle.upload;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.filebot.ResourceManager;

class StatusRenderer extends DefaultTableCellRenderer {

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		String text = null;
		Icon icon = null;

		switch ((Status) value) {
		case IllegalInput:
			text = "Please select video matching video file";
			icon = ResourceManager.getIcon("status.error");
			break;
		case CheckPending:
			text = "Pending...";
			icon = ResourceManager.getIcon("worker.pending");
			break;
		case Checking:
			text = "Checking database...";
			icon = ResourceManager.getIcon("database.go");
			break;
		case CheckFailed:
			text = "Failed to check database";
			icon = ResourceManager.getIcon("database.error");
			break;
		case AlreadyExists:
			text = "Subtitle already exists in database";
			icon = ResourceManager.getIcon("database.ok");
			break;
		case Identifying:
			text = "Auto-detect missing information";
			icon = ResourceManager.getIcon("action.export");
			break;
		case IdentificationRequired:
			text = "Please select Movie / Series and Language";
			icon = ResourceManager.getIcon("dialog.continue.invalid");
			break;
		case UploadReady:
			text = "Ready for upload";
			icon = ResourceManager.getIcon("dialog.continue");
			break;
		case Uploading:
			text = "Uploading...";
			icon = ResourceManager.getIcon("database.go");
			break;
		case UploadComplete:
			text = "Upload successful";
			icon = ResourceManager.getIcon("database.ok");
			break;
		case UploadFailed:
			text = "Upload failed";
			icon = ResourceManager.getIcon("database.error");
			break;
		}

		setText(text);
		setIcon(icon);
		return this;
	}
}
