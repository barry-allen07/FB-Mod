package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.Logging.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.RegularExpressions.*;
import static net.filebot.web.WebRequest.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;

public class AcoustIDClient implements MusicIdentificationService {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(3, 1, TimeUnit.SECONDS);

	private String apikey;

	public AcoustIDClient(String apikey) {
		this.apikey = apikey;
	}

	@Override
	public String getIdentifier() {
		return "AcoustID";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.acoustid");
	}

	public Cache getCache() {
		return Cache.getCache(getName(), CacheType.Monthly);
	}

	@Override
	public Map<File, AudioTrack> lookup(Collection<File> files) throws Exception {
		Map<File, AudioTrack> results = new LinkedHashMap<File, AudioTrack>();

		for (File file : files) {
			Map<ChromaprintField, String> fp = fpcalc(file);

			// sanity check
			if (!fp.containsKey(ChromaprintField.DURATION) || !fp.containsKey(ChromaprintField.FINGERPRINT))
				continue;

			int duration = Integer.parseInt(fp.get(ChromaprintField.DURATION));
			String fingerprint = fp.get(ChromaprintField.FINGERPRINT);

			// sanity check
			if (duration < 10)
				continue;

			String response = lookup(duration, fingerprint);
			if (response != null && response.length() > 0) {
				results.put(file, parseResult(lookup(duration, fingerprint), duration));
			}
		}

		return results;
	}

	public String lookup(int duration, String fingerprint) throws Exception {
		Map<String, String> postParam = new LinkedHashMap<String, String>();
		postParam.put("duration", String.valueOf(duration));
		postParam.put("fingerprint", fingerprint);

		// e.g.
		// http://api.acoustid.org/v2/lookup?client=8XaBELgH&meta=recordings+releasegroups+compress&duration=641&fingerprint=AQABz0qUkZK4oOfhL-CPc4e5C_wW2H2QH9uDL4cvoT8UNQ-eHtsE8cceeFJx-LiiHT-aPzhxoc-Opj_eI5d2hOFyMJRzfDk-QSsu7fBxqZDMHcfxPfDIoPWxv9C1o3yg44d_3Df2GJaUQeeR-cb2HfaPNsdxHj2PJnpwPMN3aPcEMzd-_MeB_Ej4D_CLP8ghHjkJv_jh_UDuQ8xnILwunPg6hF2R8HgzvLhxHVYP_ziJX0eKPnIE1UePMByDJyg7wz_6yELsB8n4oDmDa0Gv40hf6D3CE3_wH6HFaxCPUD9-hNeF5MfWEP3SCGym4-SxnXiGs0mRjEXD6fgl4LmKWrSChzzC33ge9PB3otyJMk-IVC6R8MTNwD9qKQ_CC8kPv4THzEGZS8GPI3x0iGVUxC1hRSizC5VzoamYDi-uR7iKPhGSI82PkiWeB_eHijvsaIWfBCWH5AjjCfVxZ1TQ3CvCTclGnEMfHbnZFA8pjD6KXwd__Cn-Y8e_I9cq6CR-4S9KLXqQcsxxoWh3eMxiHI6TIzyPv0M43YHz4yte-Cv-4D16Hv9F9C9SPUdyGtZRHV-OHEeeGD--BKcjVLOK_NCDXMfx44dzHEiOZ0Z44Rf6DH5R3uiPj4d_PKolJNyRJzyu4_CTD2WOvzjKH9GPb4cUP1Av9EuQd8fGCFee4JlRHi18xQh96NLxkCgfWFKOH6WGeoe4I3za4c5hTscTPEZTES1x8kE-9MQPjT8a8gh5fPgQZtqCFj9MDvp6fDx6NCd07bjx7MLR9AhtnFnQ70GjOcV0opmm4zpY3SOa7HiwdTtyHa6NC4e-HN-OfC5-OP_gLe2QDxfUCz_0w9l65HiPAz9-IaGOUA7-4MZ5CWFOlIfe4yUa6AiZGxf6w0fFxsjTOdC6Itbh4mGD63iPH9-RFy909XAMj7mC5_BvlDyO6kGTZKJxHUd4NDwuZUffw_5RMsde5CWkJAgXnDReNEaP6DTOQ65yaD88HoeX8fge-DSeHo9Qa8cTHc80I-_RoHxx_UHeBxrJw62Q34Kd7MEfpCcu6BLeB1ePw6OO4sOF_sHhmB504WWDZiEu8sKPpkcfCT9xfej0o0lr4T5yNJeOvjmu40w-TDmqHXmYgfFhFy_M7tD1o0cO_B2ms2j-ACEEQgQgAIwzTgAGmBIKIImNQAABwgQATAlhDGCCEIGIIM4BaBgwQBogEBIOESEIA8ARI5xAhxEFmAGAMCKAURKQQpQzRAAkCCBQEAKkQYIYIQQxCixCDADCABMAE0gpJIgyxhEDiCKCCIGAEIgJIQByAhFgGACCACMRQEyBAoxQiHiCBCFOECQFAIgAABR2QAgFjCDMA0AUMIoAIMChQghChASGEGeYEAIAIhgBSErnJPPEGWYAMgw05AhiiGHiBBBGGSCQcQgwRYJwhDDhgCSCSSEIQYwILoyAjAIigBFEUQK8gAYAQ5BCAAjkjCCAEEMZAUQAZQCjCCkpCgFMCCiIcVIAZZgilAQAiSHQECOcQAQIc4QClAHAjDDGkAGAMUoBgyhihgEChFCAAWEIEYwIJYwViAAlHCBIGEIEAEIQAoBwwgwiEBAEEEOoEwBY4wRwxAhBgAcKAESIQAwwIowRFhoBhAE

		return (String) getCache().computeIfAbsent(postParam.toString(), it -> {
			REQUEST_LIMIT.acquirePermit();

			URL url = new URL("http://api.acoustid.org/v2/lookup?client=" + apikey + "&meta=recordings+releases+releasegroups+tracks+compress");
			Map<String, String> requestParam = new HashMap<String, String>();
			requestParam.put("Content-Encoding", "gzip");
			requestParam.put("Accept-Encoding", "gzip");

			return UTF_8.decode(post(url, postParam, requestParam)).toString();
		});
	}

	public AudioTrack parseResult(String json, final int targetDuration) throws IOException {
		Object data = readJson(json);

		String status = getString(data, "status");
		if (!"ok".equals(status)) {
			throw new IOException(String.format("%s responded with error: %s", getName(), status));
		}

		for (Object result : getArray(data, "results")) {
			// pick most likely matching recording
			return streamJsonObjects(result, "recordings").sorted((r1, r2) -> {
				Integer i1 = getInteger(r1, "duration");
				Integer i2 = getInteger(r2, "duration");
				return Double.compare(i1 == null ? Double.NaN : Math.abs(i1 - targetDuration), i2 == null ? Double.NaN : Math.abs(i2 - targetDuration));
			}).map((Map<?, ?> recording) -> {
				try {
					Map<?, ?> releaseGroup = getFirstMap(recording, "releasegroups");
					String artist = getString(getFirstMap(recording, "artists"), "name");
					String title = getString(recording, "title");

					if (artist == null || title == null || releaseGroup.isEmpty())
						return null;

					AudioTrack audioTrack = new AudioTrack(artist, title, null, getIdentifier());
					audioTrack.mbid = getString(result, "id");

					String type = getString(releaseGroup, "type");
					Object[] secondaryTypes = getArray(releaseGroup, "secondarytypes");
					Object[] releases = getArray(releaseGroup, "releases");

					if (releases.length == 0 || secondaryTypes.length > 0 || (!"Album".equals(type))) {
						return audioTrack; // default to simple music info if album data is undesirable
					}

					return streamJsonObjects(releases).map(release -> {
						AudioTrack thisRelease = audioTrack.clone();

						try {
							Map<?, ?> date = getMap(release, "date");
							thisRelease.albumReleaseDate = new SimpleDate(getInteger(date, "year"), getInteger(date, "month"), getInteger(date, "day"));
						} catch (Exception e) {
							thisRelease.albumReleaseDate = null;
						}

						if (thisRelease.albumReleaseDate == null || thisRelease.albumReleaseDate.getTimeStamp() >= (audioTrack.albumReleaseDate == null ? Long.MAX_VALUE : audioTrack.albumReleaseDate.getTimeStamp())) {
							return null;
						}

						Map<?, ?> medium = getFirstMap(release, "mediums");
						thisRelease.mediumIndex = getInteger(medium, "position");
						thisRelease.mediumCount = getInteger(release, "medium_count");

						Map<?, ?> track = getFirstMap(medium, "tracks");
						thisRelease.trackIndex = getInteger(track, "position");
						thisRelease.trackCount = getInteger(medium, "track_count");

						try {
							thisRelease.album = getString(release, "title");
						} catch (Exception e) {
							thisRelease.album = getString(releaseGroup, "title");
						}
						try {
							thisRelease.albumArtist = getString(getFirstMap(releaseGroup, "artists"), "name");
						} catch (Exception e) {
							thisRelease.albumArtist = null;
						}
						thisRelease.trackTitle = getString(track, "title");

						if (!"Various Artists".equalsIgnoreCase(thisRelease.albumArtist) && (thisRelease.album == null || !thisRelease.album.contains("Greatest Hits"))) {
							// full info audio track
							return thisRelease;
						}
						return null;
					}).filter(Objects::nonNull).findFirst().orElse(audioTrack); // default to simple music info if extended info is not available
				} catch (Exception e) {
					debug.log(Level.WARNING, e.getMessage(), e);
					return null;
				}
			}).filter(Objects::nonNull).sorted(new MostFieldsNotNull()).findFirst().orElse(null);
		}

		return null;
	}

	public enum ChromaprintField {
		FILE, FINGERPRINT, DURATION;
	}

	public String getChromaprintCommand() {
		// use fpcalc executable path as specified by the cmdline or default to "fpcalc" and let the shell figure it out
		return System.getProperty("net.filebot.AcoustID.fpcalc", "fpcalc");
	}

	public Map<ChromaprintField, String> fpcalc(File file) throws IOException, InterruptedException {
		Map<ChromaprintField, String> output = new EnumMap<ChromaprintField, String>(ChromaprintField.class);

		ProcessBuilder command = new ProcessBuilder(getChromaprintCommand(), file.getCanonicalPath());
		Process process = command.redirectError(Redirect.INHERIT).start();

		try (Scanner scanner = new Scanner(new InputStreamReader(process.getInputStream(), UTF_8))) {
			while (scanner.hasNextLine()) {
				String[] value = EQUALS.split(scanner.nextLine(), 2);
				if (value.length == 2) {
					try {
						output.put(ChromaprintField.valueOf(value[0]), value[1]);
					} catch (Exception e) {
						debug.warning(e::toString);
					}
				}
			}
		}

		return output;
	}

	private static class MostFieldsNotNull implements Comparator<Object> {

		@Override
		public int compare(Object o1, Object o2) {
			return Integer.compare(count(o2), count(o1));
		}

		public int count(Object o) {
			int n = 0;
			try {
				for (Field field : o.getClass().getDeclaredFields()) {
					if (field.get(o) != null) {
						n++;
					}
				}
			} catch (Exception e) {
				debug.log(Level.WARNING, e.getMessage(), e);
			}
			return n;
		}
	}

}
