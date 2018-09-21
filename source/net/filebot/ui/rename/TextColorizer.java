package net.filebot.ui.rename;

import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.ui.SwingUI.*;

import java.awt.Color;
import java.io.File;
import java.util.List;

public class TextColorizer {

	private Color pathRainbowBeginColor;
	private Color pathRainbowEndColor;

	private String before;
	private String after;

	public TextColorizer() {
		this("<html><nobr>", "</nobr></html>");
	}

	public TextColorizer(String before, String after) {
		this(before, after, new Color(0xCC3300), new Color(0x008080));
	}

	public TextColorizer(String before, String after, Color pathRainbowBeginColor, Color pathRainbowEndColor) {
		this.before = before;
		this.after = after;
		this.pathRainbowBeginColor = pathRainbowBeginColor;
		this.pathRainbowEndColor = pathRainbowEndColor;
	}

	public StringBuilder colorizePath(StringBuilder html, File file, boolean hasExtension) {
		html.append(before);

		// colorize parent path
		List<File> path = listPath(file);
		for (int i = 0; i < path.size() - 1; i++) {
			float f = (path.size() <= 2) ? 1 : (float) i / (path.size() - 2);
			Color c = interpolateHSB(pathRainbowBeginColor, pathRainbowEndColor, f);
			html.append(String.format("<span style='color:rgb(%1$d, %2$d, %3$d)'>%4$s</span><span style='color:rgb(%1$d, %2$d, %3$d)'>/</span>", c.getRed(), c.getGreen(), c.getBlue(), escapeHTML(getFolderName(path.get(i)))));
		}

		// only colorize extension
		if (hasExtension) {
			html.append(escapeHTML(getNameWithoutExtension(file.getName())));
			String extension = getExtension(file);
			if (extension != null) {
				html.append(String.format("<span style='color:#607080'>.%s</span>", escapeHTML(extension))); // highlight extension
			}
		} else {
			html.append(file.getName());
		}

		return html.append(after);
	}

}
