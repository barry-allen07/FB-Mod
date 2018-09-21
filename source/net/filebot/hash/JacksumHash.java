
package net.filebot.hash;

import jonelo.jacksum.algorithm.AbstractChecksum;
import jonelo.jacksum.algorithm.Edonkey;

public class JacksumHash implements Hash {

	private final AbstractChecksum checksum;

	public JacksumHash(AbstractChecksum checksum) {
		this.checksum = checksum;
	}

	@Override
	public void update(byte[] bytes, int off, int len) {
		checksum.update(bytes, off, len);
	}

	@Override
	public String digest() {
		return checksum.getFormattedValue();
	}

	public static Hash newED2K() {
		try {
			return new JacksumHash(new Edonkey());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
