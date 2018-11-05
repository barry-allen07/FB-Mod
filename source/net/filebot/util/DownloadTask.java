package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.SwingWorker;

public class DownloadTask extends SwingWorker<ByteBuffer, Void> {

	public static final String DOWNLOAD_STATE = "download state";
	public static final String DOWNLOAD_PROGRESS = "download progress";

	public static enum DownloadState {
		PENDING, CONNECTING, DOWNLOADING, DONE
	}

	private URL url;

	private long contentLength = -1;
	private DownloadState state = DownloadState.PENDING;

	private Map<String, String> postParameters;
	private Map<String, String> requestHeaders;
	private Map<String, List<String>> responseHeaders;

	public DownloadTask(URL url) {
		this.url = url;
	}

	protected HttpURLConnection createConnection() throws Exception {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		if (requestHeaders != null) {
			for (Entry<String, String> entry : requestHeaders.entrySet()) {
				connection.addRequestProperty(entry.getKey(), entry.getValue());
			}
		}

		return connection;
	}

	@Override
	protected ByteBuffer doInBackground() throws Exception {
		setDownloadState(DownloadState.CONNECTING);

		HttpURLConnection connection = createConnection();

		if (postParameters != null) {
			ByteBuffer postData = UTF_8.encode(encodeParameters(postParameters, true));

			// add content type and content length headers
			connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.addRequestProperty("Content-Length", String.valueOf(postData.remaining()));

			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			// write post data
			WritableByteChannel out = Channels.newChannel(connection.getOutputStream());
			out.write(postData);
			out.close();
		}

		contentLength = connection.getContentLength();

		responseHeaders = connection.getHeaderFields();

		setDownloadState(DownloadState.DOWNLOADING);

		ReadableByteChannel in = Channels.newChannel(connection.getInputStream());
		ByteBufferOutputStream buffer = new ByteBufferOutputStream((int) (contentLength > 0 ? contentLength : BUFFER_SIZE));

		try {
			while (!isCancelled() && ((buffer.transferFrom(in)) >= 0)) {

				firePropertyChange(DOWNLOAD_PROGRESS, null, buffer.position());

				if (isContentLengthKnown()) {
					setProgress((int) ((buffer.position() * 100) / contentLength));
				}
			}
		} catch (IOException e) {
			// if the content length is not known in advance an IOException (Premature EOF)
			// is always thrown after all the data has been read
			if (isContentLengthKnown())
				throw e;

		} finally {
			in.close();

			// download either finished or an exception is thrown
			setDownloadState(DownloadState.DONE);
		}

		return buffer.getByteBuffer();
	}

	protected void setDownloadState(DownloadState state) {
		this.state = state;
		firePropertyChange(DOWNLOAD_STATE, null, state);
	}

	public DownloadState getDownloadState() {
		return state;
	}

	public URL getUrl() {
		return url;
	}

	public boolean isContentLengthKnown() {
		return contentLength >= 0;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void setRequestHeaders(Map<String, String> requestHeaders) {
		this.requestHeaders = new HashMap<String, String>(requestHeaders);
	}

	public void setPostParameters(Map<String, String> postParameters) {
		this.postParameters = new HashMap<String, String>(postParameters);
	}

	public Map<String, List<String>> getResponseHeaders() {
		return responseHeaders;
	}

	public Map<String, String> getPostParameters() {
		return postParameters;
	}

	public Map<String, String> getRequestHeaders() {
		return requestHeaders;
	}

}
