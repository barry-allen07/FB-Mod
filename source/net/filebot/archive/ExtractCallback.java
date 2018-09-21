
package net.filebot.archive;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZipException;


class ExtractCallback implements IArchiveExtractCallback {

	private IInArchive inArchive;
	private ExtractOutProvider extractOut;

	private ExtractOutStream output = null;


	public ExtractCallback(IInArchive inArchive, ExtractOutProvider extractOut) {
		this.inArchive = inArchive;
		this.extractOut = extractOut;
	}


	@Override
	public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException {
		if (extractAskMode != ExtractAskMode.EXTRACT) {
			return null;
		}

		boolean isFolder = (Boolean) inArchive.getProperty(index, PropID.IS_FOLDER);
		if (isFolder) {
			return null;
		}

		String path = (String) inArchive.getProperty(index, PropID.PATH);
		try {
			OutputStream target = extractOut.getStream(new File(path));
			if (target == null) {
				return null;
			}

			output = new ExtractOutStream(target);
			return output;
		} catch (IOException e) {
			throw new SevenZipException(e);
		}
	}


	@Override
	public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException {
	}


	@Override
	public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
		if (output != null) {
			try {
				output.close();
			} catch (IOException e) {
				throw new SevenZipException(e);
			} finally {
				output = null;
			}
		}

		if (extractOperationResult != ExtractOperationResult.OK) {
			throw new SevenZipException("Extraction Error: " + extractOperationResult);
		}
	}


	@Override
	public void setCompleted(long completeValue) throws SevenZipException {
	}


	@Override
	public void setTotal(long total) throws SevenZipException {
	}

}
