package net.filebot.media;

import java.time.Duration;

public interface MediaCharacteristics extends AutoCloseable {

	String getVideoCodec();

	String getAudioCodec();

	String getAudioLanguage();

	String getSubtitleCodec();

	Duration getDuration();

	Integer getWidth();

	Integer getHeight();

	Float getFrameRate();

}
