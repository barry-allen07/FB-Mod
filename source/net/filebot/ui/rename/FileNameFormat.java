package net.filebot.ui.rename;

import java.io.File;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

import net.filebot.util.FileUtilities;

public class FileNameFormat extends Format {

	@Override
	public StringBuffer format(Object obj, StringBuffer sb, FieldPosition pos) {
		return sb.append(FileUtilities.getName((File) obj));
	}

	@Override
	public Object parseObject(String source, ParsePosition pos) {
		return new File(source);
	}
}
