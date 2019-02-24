
package net.filebot.archive;


import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.SevenZipException;


class ExtractOutStream implements ISequentialOutStream, Closeable {

	private OutputStream out;


	public ExtractOutStream(OutputStream out) {
		this.out = out;
	}


	@Override
	public int write(byte[] data) throws SevenZipException {
		try {
			out.write(data);
		} catch (IOException e) {
			throw new SevenZipException(e);
		}
		return data.length; // return amount of proceed data
	}


	@Override
	public void close() throws IOException {
		out.close();
	}

}
