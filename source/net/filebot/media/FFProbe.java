package net.filebot.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;

public class FFProbe implements MediaCharacteristics {

	protected String getFFProbeCommand() {
		return System.getProperty("net.filebot.media.ffprobe", "ffprobe");
	}

	protected Map<String, Object> parse(File file) throws IOException, InterruptedException {
		ProcessBuilder processBuilder = new ProcessBuilder(getFFProbeCommand(), "-show_streams", "-show_format", "-print_format", "json", "-v", "error", file.getCanonicalPath());

		processBuilder.directory(file.getParentFile());
		processBuilder.redirectError(Redirect.INHERIT);

		Process process = processBuilder.start();

		// parse process standard output
		Map<String, Object> json = (Map) JsonReader.jsonToJava(process.getInputStream(), singletonMap(JsonReader.USE_MAPS, true));

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException(String.format("%s failed with exit code %d", processBuilder.command(), exitCode));
		}

		// group video / audio / subtitle streams together
		return json;
	}

	private Map<String, Object> json;

	public synchronized FFProbe open(File file) throws IOException, InterruptedException {
		json = parse(file);
		return this;
	}

	@Override
	public synchronized void close() {
		json = null;
	}

	@Override
	public String getVideoCodec() {
		return getString("video", "codec_name");
	}

	@Override
	public String getAudioCodec() {
		return getString("audio", "codec_name");
	}

	@Override
	public String getAudioLanguage() {
		return getString("audio", "tags", "language");
	}

	@Override
	public String getSubtitleCodec() {
		return getString("subtitle", "codec_name");
	}

	@Override
	public Duration getDuration() {
		long d = (long) Double.parseDouble(getFormat().get("duration").toString()) * 1000;
		return Duration.ofMillis(d);
	}

	@Override
	public Integer getWidth() {
		return getInteger("video", "width");
	}

	@Override
	public Integer getHeight() {
		return getInteger("video", "height");
	}

	@Override
	public Float getFrameRate() {
		return find("video", "avg_frame_rate").map(fps -> {
			switch (fps) {
			case "500/21":
				return 23.976f; // normalize FPS value (using MediaInfo standards)
			default:
				return Float.parseFloat(fps);
			}
		}).get();
	}

	public Map<String, Object> getFormat() {
		return (Map) json.get("format");
	}

	public List<Map<String, Object>> getStreams() {
		return (List) asList((Object[]) json.get("streams"));
	}

	protected String getString(String streamKind, String key) {
		return stream(streamKind, key).map(Objects::toString).collect(joining(" "));
	}

	protected String getString(String streamKind, String objectKey, String valueKey) {
		return stream(streamKind, objectKey).map(t -> ((Map) t).get(valueKey)).map(Objects::toString).collect(joining(" "));
	}

	protected Stream<Object> stream(String streamKind, String property) {
		return getStreams().stream().filter(s -> streamKind.equals(s.get("codec_type"))).map(s -> s.get(property)).filter(Objects::nonNull);
	}

	protected Integer getInteger(String streamKind, String property) {
		return find(streamKind, property).map(Integer::parseInt).get();
	}

	protected Optional<String> find(String streamKind, String property) {
		return stream(streamKind, property).map(Objects::toString).findFirst();
	}

	@Override
	public String toString() {
		return JsonWriter.objectToJson(json);
	}

}
