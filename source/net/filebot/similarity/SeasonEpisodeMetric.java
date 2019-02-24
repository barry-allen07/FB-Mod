package net.filebot.similarity;

import java.io.File;
import java.util.Collection;

import net.filebot.similarity.SeasonEpisodeMatcher.SxE;

public class SeasonEpisodeMetric implements SimilarityMetric {

	private SeasonEpisodeMatcher seasonEpisodeMatcher;

	public SeasonEpisodeMetric() {
		this.seasonEpisodeMatcher = new SeasonEpisodeMatcher(null, false);
	}

	public SeasonEpisodeMetric(SeasonEpisodeMatcher seasonEpisodeMatcher) {
		this.seasonEpisodeMatcher = seasonEpisodeMatcher;
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		Collection<SxE> sxeVector1 = parse(o1);
		if (sxeVector1 == null || sxeVector1.isEmpty())
			return 0;

		Collection<SxE> sxeVector2 = parse(o2);
		if (sxeVector2 == null || sxeVector2.isEmpty())
			return 0;

		float similarity = -1;
		for (SxE sxe1 : sxeVector1) {
			for (SxE sxe2 : sxeVector2) {
				if (sxe1.season == sxe2.season && sxe1.episode == sxe2.episode && sxe1.season >= 0 && sxe2.season >= 0) {
					// vectors have at least one perfect episode match in common (require season >= 0 as to put less trust in single-number matches)
					return 1;
				}

				if ((sxe1.season >= 0 && sxe1.season == sxe2.season) || (sxe1.episode >= 0 && sxe1.episode == sxe2.episode)) {
					// at least we have a partial match
					similarity = 0.5f;
				}
			}
		}

		return similarity;
	}

	protected Collection<SxE> parse(Object object) {
		if (object instanceof File) {
			return seasonEpisodeMatcher.match((File) object);
		}

		return seasonEpisodeMatcher.match(object.toString());
	}

}
