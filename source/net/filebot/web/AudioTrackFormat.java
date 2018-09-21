
package net.filebot.web;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

public class AudioTrackFormat extends Format {

	@Override
	public StringBuffer format(Object obj, StringBuffer sb, FieldPosition pos) {
		return sb.append(obj.toString());
	}

	@Override
	public AudioTrack parseObject(String source, ParsePosition pos) {
		String[] s = source.split(" - ", 2);
		if (s.length == 2) {
			pos.setIndex(source.length());
			return new AudioTrack(s[0].trim(), s[1].trim(), "VA", null);
		} else {
			pos.setErrorIndex(0);
			return null;
		}
	}

}
