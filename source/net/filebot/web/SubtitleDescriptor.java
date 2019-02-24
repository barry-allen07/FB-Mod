
package net.filebot.web;


import java.nio.ByteBuffer;

import net.filebot.vfs.FileInfo;


public interface SubtitleDescriptor extends FileInfo {

	@Override
	String getName();


	String getLanguageName();


	@Override
	String getType();


	ByteBuffer fetch() throws Exception;

}
