package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.util.Arrays;
import java.util.List;

public class MultiEpisode extends Episode {

	protected Episode[] episodes;

	public MultiEpisode() {
		// used by deserializer
	}

	public MultiEpisode(Episode... episodes) {
		this.episodes = episodes.clone();
	}

	public MultiEpisode(List<Episode> episodes) {
		this.episodes = episodes.toArray(new Episode[0]);
	}

	public List<Episode> getEpisodes() {
		return unmodifiableList(asList(episodes));
	}

	public String getSeriesName() {
		return episodes[0].getSeriesName();
	}

	public Integer getEpisode() {
		return episodes[0].getEpisode();
	}

	public Integer getSeason() {
		return episodes[0].getSeason();
	}

	public String getTitle() {
		return EpisodeFormat.SeasonEpisode.formatMultiTitle(getEpisodes());
	}

	public Integer getAbsolute() {
		return episodes[0].getAbsolute();
	}

	public Integer getSpecial() {
		return episodes[0].getSpecial();
	}

	public SimpleDate getAirdate() {
		return episodes[0].getAirdate();
	}

	public Integer getId() {
		return episodes[0].getId();
	}

	public SeriesInfo getSeriesInfo() {
		return episodes[0].getSeriesInfo();
	}

	public List<Integer> getNumbers() {
		return stream(episodes).flatMap(e -> e.getNumbers().stream()).collect(toList());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MultiEpisode) {
			MultiEpisode other = (MultiEpisode) obj;
			return Arrays.equals(episodes, other.episodes);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(episodes);
	}

	@Override
	public MultiEpisode clone() {
		return new MultiEpisode(episodes);
	}

	@Override
	public String toString() {
		return EpisodeFormat.SeasonEpisode.formatMultiEpisode(getEpisodes());
	}

}
