package net.filebot.ui.subtitle.upload;

import static java.util.Arrays.*;

import java.io.File;

import net.filebot.Language;
import net.filebot.util.ui.AbstractBean;
import net.filebot.web.Movie;

class SubtitleMapping extends AbstractBean {

	private Movie identity;
	private File video;
	private File subtitle;
	private Language language;

	private Status status;

	public SubtitleMapping(File subtitle, File video, Language language) {
		this.subtitle = subtitle;
		this.video = video;
		this.language = language;

		this.status = (video == null || subtitle == null) ? Status.IllegalInput : Status.CheckPending;
	}

	public boolean isCheckReady() {
		return subtitle != null && status == Status.CheckPending;
	}

	public boolean isUploadReady() {
		return identity != null && subtitle != null && video != null && language != null && status == Status.UploadReady;
	}

	public Object getGroup() {
		return asList(identity.getImdbId(), language.getCode());
	}

	public Object getIdentity() {
		return identity;
	}

	public File getSubtitle() {
		return subtitle;
	}

	public File getVideo() {
		return video;
	}

	public Language getLanguage() {
		return language;
	}

	public Status getStatus() {
		return status;
	}

	public void setVideo(File video) {
		this.video = video;
		firePropertyChange("video", null, this.video);
	}

	public void setIdentity(Movie identity) {
		this.identity = identity;
		firePropertyChange("identity", null, this.identity);
	}

	public void setLanguage(Language language) {
		this.language = language;
		firePropertyChange("language", null, this.language);
	}

	public void setState(Status status) {
		this.status = status;
		firePropertyChange("status", null, this.status);
	}

	@Override
	public String toString() {
		return asList(identity, video, subtitle, language, status).toString();
	};

}
