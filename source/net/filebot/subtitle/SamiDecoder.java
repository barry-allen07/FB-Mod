package net.filebot.subtitle;

import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.similarity.Normalization.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class SamiDecoder implements SubtitleDecoder {

	@Override
	public Stream<SubtitleElement> decode(String file) {
		List<SubtitleElement> subtitles = new ArrayList<SubtitleElement>();

		Matcher matcher = Pattern.compile("<SYNC(.*?)>", Pattern.CASE_INSENSITIVE).matcher(file);

		long previousSyncStart = -1;
		long previousSyncEnd = -1;
		int previousSequenceEnd = -1;

		while (matcher.find()) {
			Element sync = Jsoup.parseBodyFragment(matcher.group()).select("sync").first();

			long nextSyncStart = getLongAttribute(sync, "start");
			long nextSyncEnd = getLongAttribute(sync, "end");

			if (previousSequenceEnd > 0) {
				// use Start time of the next subtitle element as End time of the previous one by default
				if (previousSyncEnd < 0) {
					previousSyncEnd = nextSyncStart;
				}

				SubtitleElement subtitle = getSubtitle(previousSyncStart, previousSyncEnd, file.subSequence(previousSequenceEnd, matcher.start()));
				if (subtitle != null) {
					subtitles.add(subtitle);
				}
			}

			if (nextSyncStart >= 0) {
				previousSyncStart = nextSyncStart;
				previousSyncEnd = nextSyncEnd;
				previousSequenceEnd = matcher.end();
			}
		}

		// last element if any
		if (previousSequenceEnd > 0) {
			// if end time is not known, then just set subtitle duration to 2 seconds
			if (previousSyncEnd < 0) {
				previousSyncEnd = previousSyncStart + 2000;
			}

			SubtitleElement subtitle = getSubtitle(previousSyncStart, previousSyncEnd, file.subSequence(previousSequenceEnd, file.length()));
			if (subtitle != null) {
				subtitles.add(subtitle);
			}
		}

		return subtitles.stream();
	}

	private SubtitleElement getSubtitle(long start, long end, CharSequence fragment) {
		if (start >= 0 && end >= 0) {
			Document document = Jsoup.parseBodyFragment(fragment.toString());
			String text = document.select("p").stream().map(p -> p.text()).map(s -> replaceSpace(s, " ")).filter(s -> s.length() > 0).collect(joining("\n")).trim();

			if (text.length() > 0) {
				return new SubtitleElement(start, end, text);
			}
		}

		return null;
	}

	private long getLongAttribute(Element node, String key) {
		if (node != null) {
			String value = node.attr(key);

			if (value.length() > 0) {
				try {
					return Long.parseLong(value);
				} catch (Exception e) {
					debug.warning(cause(e));
				}
			}
		}

		return -1;
	}

}
