package net.filebot.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum BOM {

	UTF_8((byte) 0xEF, (byte) 0xBB, (byte) 0xBF),

	UTF_16BE((byte) 0xFE, (byte) 0xFF),

	UTF_16LE((byte) 0xFF, (byte) 0xFE),

	UTF_32BE((byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF),

	UTF_32LE((byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00),

	GB_18030((byte) 0x84, (byte) 0x31, (byte) 0x95, (byte) 0x33);

	public static final int SIZE = 4;

	private byte[] bom;

	BOM(byte... bom) {
		this.bom = bom;
	}

	public int size() {
		return bom.length;
	}

	public boolean matches(byte[] bytes) {
		if (bytes.length < bom.length) {
			return false;
		}

		for (int i = 0; i < bom.length; i++) {
			if (bom[i] != bytes[i]) {
				return false;
			}
		}

		return true;
	}

	public Charset getCharset() {
		switch (this) {
		case UTF_8:
			return StandardCharsets.UTF_8;
		case UTF_16BE:
			return StandardCharsets.UTF_16BE;
		case UTF_16LE:
			return StandardCharsets.UTF_16LE;
		case UTF_32BE:
			return Charset.forName("UTF-32BE");
		case UTF_32LE:
			return Charset.forName("UTF-32LE");
		case GB_18030:
			return Charset.forName("GB18030");
		}
		return null;
	}

	public static BOM detect(byte[] bytes) {
		for (BOM bom : values()) {
			if (bom.matches(bytes)) {
				return bom;
			}
		}
		return null;
	}

}
