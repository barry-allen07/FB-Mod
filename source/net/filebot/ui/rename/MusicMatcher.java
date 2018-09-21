package net.filebot.ui.rename;

import static net.filebot.MediaTypes.*;
import static net.filebot.util.FileUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.filebot.similarity.Match;
import net.filebot.web.AudioTrack;
import net.filebot.web.MusicIdentificationService;
import net.filebot.web.SortOrder;

class MusicMatcher implements AutoCompleteMatcher {

	private MusicIdentificationService[] services;

	public MusicMatcher(MusicIdentificationService... services) {
		this.services = services;
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		LinkedHashSet<File> remaining = new LinkedHashSet<File>(filter(files, AUDIO_FILES, VIDEO_FILES));

		// check audio files against all services
		for (int i = 0; i < services.length && remaining.size() > 0; i++) {
			services[i].lookup(remaining).forEach((k, v) -> {
				if (v != null) {
					matches.add(new Match<File, AudioTrack>(k, v.clone()));
					remaining.remove(k);
				}
			});
		}

		return matches;
	}

}
