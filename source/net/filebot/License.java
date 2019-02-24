package net.filebot;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.util.PGP.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.filebot.util.SystemProperty;
import net.filebot.web.WebRequest;

public class License {

	private final String product;
	private final String id;
	private final Instant expires;

	private final Exception error;

	private License(String product, String id, Instant expires) {
		this.product = product;
		this.id = id;
		this.expires = expires;
		this.error = null;
	}

	private License(Exception error) {
		this.product = null;
		this.id = null;
		this.expires = null;
		this.error = error;
	}

	public boolean expires() {
		return expires != null;
	}

	public boolean error() {
		return error != null;
	}

	public License check() throws Exception {
		if (error()) {
			throw error;
		}

		if (expires()) {
			checkExpirationDate(expires);
		}

		return this;
	}

	@Override
	public String toString() {
		// FileBot License T1000
		StringBuilder s = new StringBuilder().append(product).append(" License ").append(id);

		// Valid-Until: 2019-07-04
		if (expires()) {
			s.append(" (Valid-Until: ").append(formatExpirationDate(expires)).append(")");
		}

		return s.toString();
	}

	public static License parseLicenseFile(File file) {
		try {
			// require non-empty license file
			if (file.length() <= 0) {
				throw new FileNotFoundException("UNREGISTERED");
			}

			// read and verify license file
			return parseLicenseText(new String(Files.readAllBytes(file.toPath()), UTF_8), false);
		} catch (Exception error) {
			return new License(error);
		}
	}

	public static License parseLicenseText(String psm, boolean strict) {
		try {
			// verify and get clear signed content
			Map<String, String> properties = getProperties(psm);

			String product = properties.get("Product");
			String id = properties.get("Order");
			Instant expires = Optional.ofNullable(properties.get("Valid-Until")).map(License::parseExpirationDate).orElse(null);

			// check validity
			checkExpirationDate(expires);

			// verify license online
			verifyLicense(id, psm, strict);

			return new License(product, id, expires);
		} catch (Exception error) {
			return new License(error);
		}
	}

	private static Map<String, String> getProperties(String psm) throws Exception {
		String msg = verifyClearSignMessage(psm, License.class.getResourceAsStream("license.key"));

		return NEWLINE.splitAsStream(msg).map(s -> s.split(": ", 2)).collect(toMap(a -> a[0], a -> a[1]));
	}

	private static Instant parseExpirationDate(String date) {
		return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1).toInstant();
	}

	private static String formatExpirationDate(Instant expires) {
		return expires.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
	}

	private static void checkExpirationDate(Instant expires) throws Exception {
		if (expires != null && Instant.now().isAfter(expires)) {
			throw new IllegalStateException("EXPIRED since " + formatExpirationDate(expires));
		}
	}

	private static void verifyLicense(String id, String psm, boolean strict) throws Exception {
		Cache cache = Cache.getCache(getApplicationName(), CacheType.Monthly);

		// force verify request
		if (strict) {
			cache.remove(id);
		}

		Object json = cache.json(id, i -> new URL("https://license.filebot.net/verify/" + i)).fetch((url, modified) -> {
			// log license activation requests
			debug.warning(format("Activate License %s", id));

			return WebRequest.post(url, psm.getBytes(UTF_8), "application/octet-stream", getRequestParameters());
		}).expire(Cache.ONE_MONTH).get();

		// flush all cache from memory to the disk store
		cache.flush();

		if (getInteger(json, "status") != 200) {
			throw new IllegalStateException(getString(json, "message"));
		}
	}

	private static Map<String, String> getRequestParameters() {
		Map<String, String> parameters = new LinkedHashMap<String, String>();

		// add standard HTTP headers
		parameters.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));

		// add custom HTTP headers for user statistics
		parameters.put("X-FileBot-OS", getSystemIdentifier());
		parameters.put("X-FileBot-PKG", getApplicationDeployment().toUpperCase());

		return parameters;
	}

	public static final SystemProperty<File> FILE = SystemProperty.of("net.filebot.license", File::new, ApplicationFolder.AppData.resolve("license.txt"));
	public static final MemoizedResource<License> INSTANCE = Resource.lazy(() -> parseLicenseFile(FILE.get()));

	public static License importLicense(String psm) throws Exception {
		// lock memoized resource while validating and setting a new license
		synchronized (License.INSTANCE) {
			// check if license file is valid and not expired
			License license = parseLicenseText(psm, true).check();

			// write to default license file path
			Files.write(License.FILE.get().toPath(), psm.getBytes(UTF_8));

			// clear memoized instance and reload on next access
			License.INSTANCE.clear();

			// return valid license object
			return license;
		}
	}

}
