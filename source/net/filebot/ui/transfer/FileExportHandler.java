
package net.filebot.ui.transfer;


import java.io.File;
import java.io.IOException;


public interface FileExportHandler {

	public boolean canExport();


	public void export(File file) throws IOException;


	public String getDefaultFileName();

}
