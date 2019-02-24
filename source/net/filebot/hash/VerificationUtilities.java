package net.filebot.hash;

import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerificationUtilities {

	/**
	 * A {@link Pattern} that will match checksums enclosed in brackets ("[]" or "()"). A checksum string is a hex number with at least 8 digits. Capturing group 0 will contain the matched checksum string.
	 */
	public static final Pattern EMBEDDED_CHECKSUM = Pattern.compile("(?<=\\[|\\()(\\p{XDigit}{8})(?=\\]|\\))");

	public static String getEmbeddedChecksum(CharSequence string) {
		Matcher matcher = EMBEDDED_CHECKSUM.matcher(string);
		String embeddedChecksum = null;

		// get last match
		while (matcher.find()) {
			embeddedChecksum = matcher.group();
		}

		return embeddedChecksum;
	}

	public static String getHashFromVerificationFile(File file, HashType type, int maxDepth) throws IOException {
		return getHashFromVerificationFile(file.getParentFile(), file, type, 0, maxDepth);
	}

	private static String getHashFromVerificationFile(File folder, File target, HashType type, int depth, int maxDepth) throws IOException {
		// stop if we reached max depth or the file system root
		if (folder == null || depth > maxDepth)
			return null;

		// scan all sfv files in this folder
		for (File verificationFile : getChildren(folder, type.getFilter())) {
			VerificationFileReader parser = new VerificationFileReader(createTextReader(verificationFile), type.getFormat());

			try {
				while (parser.hasNext()) {
					Entry<File, String> entry = parser.next();

					// resolve relative file path
					File file = new File(folder, entry.getKey().getPath());

					if (target.equals(file)) {
						return entry.getValue();
					}
				}
			} finally {
				parser.close();
			}
		}

		return getHashFromVerificationFile(folder.getParentFile(), target, type, depth + 1, maxDepth);
	}

	public static HashType getHashType(File verificationFile) {
		for (HashType hashType : HashType.values()) {
			if (hashType.getFilter().accept(verificationFile))
				return hashType;
		}

		return null;
	}

	public static HashType getHashTypeByExtension(String extension) {
		for (HashType hashType : HashType.values()) {
			if (hashType.getFilter().acceptExtension(extension))
				return hashType;
		}

		return null;
	}

	public static String computeHash(File file, HashType type) throws IOException, InterruptedException {
		Hash hash = type.newHash();

		// calculate checksum
		InputStream in = new FileInputStream(file);

		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			int len = 0;

			while ((len = in.read(buffer)) >= 0) {
				hash.update(buffer, 0, len);

				// make this long-running operation interruptible
				if (Thread.interrupted())
					throw new InterruptedException();
			}
		} finally {
			in.close();
		}

		return hash.digest();
	}

	public static String crc32(File file) throws IOException, InterruptedException {
		return computeHash(file, HashType.SFV);
	}

	public static String sha256(File file) throws IOException, InterruptedException {
		return computeHash(file, HashType.SHA256);
	}

	private VerificationUtilities() {
		throw new UnsupportedOperationException();
	}

}
