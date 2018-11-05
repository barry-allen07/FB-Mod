
package net.filebot.subtitle;


import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;


class SubtitleTimeFormat extends DateFormat {

	public SubtitleTimeFormat() {
		// calendar without any kind of special handling for time zone and daylight saving time
		calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
	}


	@Override
	public StringBuffer format(Date date, StringBuffer sb, FieldPosition pos) {
		// e.g. 1:42:52.42
		calendar.setTime(date);

		sb.append(String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)));
		sb.append(':').append(String.format("%02d", calendar.get(Calendar.MINUTE)));
		sb.append(':').append(String.format("%02d", calendar.get(Calendar.SECOND)));

		String millis = String.format("%03d", calendar.get(Calendar.MILLISECOND));
		sb.append('.').append(millis.substring(0, 2));

		return sb;
	}


	private final Pattern delimiter = Pattern.compile("[:.]");


	@Override
	public Date parse(String source, ParsePosition pos) {
		String[] split = delimiter.split(source, 4);

		// reset state
		calendar.clear();

		try {
			// handle hours:minutes:seconds
			calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(split[0]));
			calendar.set(Calendar.MINUTE, Integer.parseInt(split[1]));
			calendar.set(Calendar.SECOND, Integer.parseInt(split[2]));

			// handle hundredth seconds
			calendar.set(Calendar.MILLISECOND, Integer.parseInt(split[3]) * 10);
		} catch (Exception e) {
			// cannot parse input
			pos.setErrorIndex(0);
			return null;
		}

		// update position
		pos.setIndex(source.length());
		return calendar.getTime();
	}
}
