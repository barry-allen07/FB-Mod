package net.filebot.web;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public interface MusicIdentificationService extends Datasource {

	Map<File, AudioTrack> lookup(Collection<File> files) throws Exception;

}
