
package net.filebot.subtitle;


import java.io.Closeable;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;


public class SubRipWriter implements Closeable {

	private final DateFormat timeFormat;
	private final Formatter out;

	private int lineNumber = 0;


	public SubRipWriter(Appendable out) {
		this.out = new Formatter(out, Locale.ROOT);

		// format used to create time stamps (e.g. 00:02:26,407 --> 00:02:31,356)
		timeFormat = new SimpleDateFormat("HH:mm:ss,SSS", Locale.ROOT);
		timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}


	public void write(SubtitleElement element) {
		//		write a single subtitle in SubRip format, e.g.
		//		1
		//		00:00:20,000 --> 00:00:24,400
		//		Altocumulus clouds occur between six thousand
		out.format("%d%n", ++lineNumber);
		out.format("%s --> %s%n", timeFormat.format(element.getStart()), timeFormat.format(element.getEnd()));
		out.format("%s%n%n", element.getText());
	}


	@Override
	public void close() throws IOException {
		out.close();
	}

}
