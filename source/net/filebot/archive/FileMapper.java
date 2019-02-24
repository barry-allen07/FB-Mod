package net.filebot.archive;

import static java.util.stream.Collectors.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;

import net.filebot.vfs.FileInfo;

public class FileMapper implements ExtractOutProvider {

	private File outputDir;
	private boolean flatten;

	public FileMapper(File outputDir) {
		this(outputDir, false);
	};

	public FileMapper(File outputDir, boolean flatten) {
		this.outputDir = outputDir;
		this.flatten = flatten;
	};

	public File getOutputDir() {
		return outputDir;
	}

	public File getOutputFile(File entry) {
		return new File(outputDir, flatten ? entry.getName() : entry.getPath());
	}

	@Override
	public OutputStream getStream(File entry) throws IOException {
		File outputFile = getOutputFile(entry);
		File outputFolder = outputFile.getParentFile();

		// create parent folder if necessary
		if (!outputFolder.isDirectory() && !outputFolder.mkdirs()) {
			throw new IOException("Failed to create folder: " + outputFolder);
		}

		return new FileOutputStream(outputFile);
	}

	public FileFilter newPathFilter(Collection<FileInfo> selection) {
		return newPathFilter(selection.stream().map(FileInfo::getPath).collect(toSet()));
	}

	public FileFilter newPathFilter(Set<String> selection) {
		return f -> selection.contains(getOutputFile(f).getPath());
	}

}
