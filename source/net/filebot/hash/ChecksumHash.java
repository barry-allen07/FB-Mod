
package net.filebot.hash;


import java.util.zip.Checksum;


public class ChecksumHash implements Hash {

	private final Checksum checksum;


	public ChecksumHash(Checksum checksum) {
		this.checksum = checksum;
	}


	@Override
	public void update(byte[] bytes, int off, int len) {
		checksum.update(bytes, off, len);
	}


	@Override
	public String digest() {
		return String.format("%08X", checksum.getValue());
	}

}
