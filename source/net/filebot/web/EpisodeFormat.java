package net.filebot.web;

import static java.util.stream.Collectors.*;
import static net.filebot.similarity.Normalization.*;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Collection;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.filebot.similarity.Normalization;

public class EpisodeFormat extends Format {

	public static final EpisodeFormat SeasonEpisode = new EpisodeFormat();

	@Override
	public StringBuffer format(Object obj, StringBuffer sb, FieldPosition pos) {
		if (obj instanceof MultiEpisode) {
			return sb.append(formatMultiEpisode(((MultiEpisode) obj).getEpisodes()));
		}

		// format episode object, e.g. Dark Angel - 3x01 - Labyrinth [2009-06-01]
		Episode episode = (Episode) obj;

		// episode number is most likely a number but could also be some kind of special identifier (e.g. Special)
		String episodeNumber = episode.getEpisode() != null ? String.format("%02d", episode.getEpisode()) : null;

		// series name should not be empty or null
		sb.append(episode.getSeriesName());

		if (episode.getSeason() != null) {
			// season and episode
			sb.append(" - ").append(episode.getSeason()).append('x');

			if (episode.getEpisode() != null) {
				sb.append(String.format("%02d", episode.getEpisode()));
			} else if (episode.getSpecial() != null) {
				sb.append("Special " + episode.getSpecial());
			}
		} else {
			// episode, but no season
			if (episode.getEpisode() != null) {
				sb.append(" - ").append(episodeNumber);
			} else if (episode.getSpecial() != null) {
				sb.append(" - ").append("Special " + episode.getSpecial());
			}
		}
		sb.append(" - ").append(episode.getTitle());
		return sb;
	}

	public String formatMultiEpisode(Collection<Episode> episodes) {
		Function<Episode, String> seriesName = it -> it.getSeriesName();
		Function<Episode, String> episodeNumber = it -> formatSxE(it);
		Function<Episode, String> episodeTitle = it -> it.getTitle() == null ? "" : removeTrailingBrackets(it.getTitle());

		return Stream.of(seriesName, episodeNumber, episodeTitle).map(f -> {
			return episodes.stream().map(f::apply).filter(s -> s.length() > 0).distinct().collect(joining(" & "));
		}).collect(joining(" - "));
	}

	public String formatSxE(Episode episode) {
		if (episode instanceof MultiEpisode) {
			return formatMultiRangeSxE(((MultiEpisode) episode).getEpisodes());
		}

		StringBuilder sb = new StringBuilder();
		if (episode.getSeason() != null || episode.getSpecial() != null) {
			sb.append(episode.getSpecial() == null ? episode.getSeason() : 0).append('x');
		}
		if (episode.getEpisode() != null || episode.getSpecial() != null) {
			sb.append(String.format("%02d", episode.getSpecial() == null ? episode.getEpisode() : episode.getSpecial()));
		}
		return sb.toString();
	}

	public String formatS00E00(Episode episode) {
		if (episode instanceof MultiEpisode) {
			return formatMultiRangeS00E00(((MultiEpisode) episode).getEpisodes());
		}

		StringBuilder sb = new StringBuilder();
		if (episode.getSeason() != null || episode.getSpecial() != null) {
			sb.append(String.format("S%02d", episode.getSpecial() == null ? episode.getSeason() : 0));
		}
		if (episode.getEpisode() != null || episode.getSpecial() != null) {
			sb.append(String.format("E%02d", episode.getSpecial() == null ? episode.getEpisode() : episode.getSpecial()));
		}
		return sb.toString();
	}

	public String formatMultiTitle(Collection<Episode> episodes) {
		return episodes.stream().map(Episode::getTitle).filter(Objects::nonNull).map(Normalization::removeTrailingBrackets).distinct().collect(joining(" & "));
	}

	public String formatMultiRangeSxE(Iterable<Episode> episodes) {
		return formatMultiRangeNumbers(episodes, "%01dx", "%02d");
	}

	public String formatMultiRangeS00E00(Iterable<Episode> episodes) {
		return formatMultiRangeNumbers(episodes, "S%02d", "E%02d");
	}

	public String formatMultiRangeNumbers(Iterable<Episode> episodes, String seasonFormat, String episodeFormat) {
		return getSeasonEpisodeNumbers(episodes).entrySet().stream().map(it -> {
			String s = it.getKey() >= 0 ? String.format(seasonFormat, it.getKey()) : "";
			return Stream.of(it.getValue().first(), it.getValue().last()).distinct().map(i -> String.format(episodeFormat, i)).collect(joining("-", s, ""));
		}).collect(joining(" - "));
	}

	private SortedMap<Integer, SortedSet<Integer>> getSeasonEpisodeNumbers(Iterable<Episode> episodes) {
		SortedMap<Integer, SortedSet<Integer>> n = new TreeMap<Integer, SortedSet<Integer>>();
		for (Episode it : episodes) {
			Integer s = it.getSeason() == null || it.getSpecial() != null ? it.getSpecial() == null ? -1 : 0 : it.getSeason();
			Integer e = it.getEpisode() == null ? it.getSpecial() == null ? -1 : it.getSpecial() : it.getEpisode();
			n.computeIfAbsent(s, key -> new TreeSet<Integer>()).add(e);
		}
		return n;
	}

	private final Pattern sxePattern = Pattern.compile("- (?:(\\d{1,2})x)?(Special )?(\\d{1,3}) -");
	private final Pattern airdatePattern = Pattern.compile("\\[(\\d{4}-\\d{1,2}-\\d{1,2})\\]");

	@Override
	public Episode parseObject(String s, ParsePosition pos) {
		StringBuilder source = new StringBuilder(s);

		Integer season = null;
		Integer episode = null;
		Integer special = null;
		SimpleDate airdate = null;

		Matcher m;

		if ((m = airdatePattern.matcher(source)).find()) {
			airdate = SimpleDate.parse(m.group(1));
			source.replace(m.start(), m.end(), ""); // remove matched part from text
		}

		if ((m = sxePattern.matcher(source)).find()) {
			season = (m.group(1) == null) ? null : Integer.parseInt(m.group(1));
			if (m.group(2) == null)
				episode = Integer.parseInt(m.group(3));
			else
				special = Integer.parseInt(m.group(3));

			source.replace(m.start(), m.end(), ""); // remove matched part from text

			// assume that all the remaining text is series name and title
			String name = source.substring(0, m.start()).trim();
			String title = source.substring(m.start()).trim();

			// did parse input
			pos.setIndex(source.length());
			return new Episode(name, season, episode, title, season == null ? episode : null, special, airdate, null, null);
		}

		// failed to parse input
		pos.setErrorIndex(0);
		return null;
	}

	@Override
	public Episode parseObject(String source) throws ParseException {
		return (Episode) super.parseObject(source);
	}

}
