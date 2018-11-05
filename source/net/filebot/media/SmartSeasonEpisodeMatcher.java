package net.filebot.media;

import static net.filebot.media.MediaDetection.*;

import java.io.File;
import java.util.List;

import net.filebot.similarity.SeasonEpisodeMatcher;

public class SmartSeasonEpisodeMatcher extends SeasonEpisodeMatcher {

	public SmartSeasonEpisodeMatcher(SeasonEpisodeFilter sanity, boolean strict) {
		super(sanity, strict);
	}

	protected String clean(CharSequence name) {
		return stripFormatInfo(name);
	}

	@Override
	public List<SxE> match(CharSequence name) {
		return super.match(clean(name));
	}

	@Override
	public List<SxE> match(File file) {
		return super.match(new File(clean(file.getPath())));
	}

	@Override
	public String head(String name) {
		return super.head(clean(name));
	}

	@Override
	protected List<String> tokenizeTail(File file) {
		List<String> tail = super.tokenizeTail(file);
		for (int i = 0; i < tail.size(); i++) {
			tail.set(i, clean(tail.get(i)));
		}
		return tail;
	}

}
