package net.filebot.subtitle;

import java.util.stream.Stream;

public interface SubtitleDecoder {

	Stream<SubtitleElement> decode(String file);

}