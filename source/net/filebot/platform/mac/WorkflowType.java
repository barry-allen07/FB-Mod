package net.filebot.platform.mac;

public enum WorkflowType {

	QuickAction, FolderAction;

	public String getFolderName() {
		switch (this) {
		case QuickAction:
			return "Quick Actions";
		default:
			return "Folder Actions";
		}
	}

	public String getLibraryPath() {
		switch (this) {
		case QuickAction:
			return "Library/Services";
		default:
			return "Library/Workflows/Applications/Folder Actions";
		}
	}

}
