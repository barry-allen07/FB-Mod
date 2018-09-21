package net.filebot.ui.subtitle.upload;

import static java.util.Arrays.*;

import java.io.File;
import java.util.List;

import net.filebot.Language;

class SubtitleGroup {

	private final SubtitleMapping[] mapping;

	public SubtitleGroup(List<SubtitleMapping> mapping) {
		this.mapping = mapping.toArray(new SubtitleMapping[mapping.size()]);
	}

	public void setState(Status status) {
		for (SubtitleMapping it : mapping) {
			it.setState(status);
		}
	}

	public boolean isUploadReady() {
		return stream(mapping).allMatch(SubtitleMapping::isUploadReady);
	}

	public Object getIdentity() {
		return mapping[0].getIdentity();
	}

	public Language getLanguage() {
		return mapping[0].getLanguage();
	}

	public File[] getVideoFiles() {
		return stream(mapping).map(SubtitleMapping::getVideo).toArray(File[]::new);
	}

	public File[] getSubtitleFiles() {
		return stream(mapping).map(SubtitleMapping::getSubtitle).toArray(File[]::new);
	}

	@Override
	public String toString() {
		return asList(getIdentity(), getLanguage(), asList(getVideoFiles()), asList(getSubtitleFiles())).toString();
	};

}
