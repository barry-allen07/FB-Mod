package net.filebot.platform.mac.xattr;

import static java.nio.charset.StandardCharsets.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Memory;

public class XAttrUtil {

	public static List<String> listXAttr(String path) {
		// get required buffer size
		long bufferLength = XAttr.INSTANCE.listxattr(path, null, 0, 0);

		if (bufferLength < 0)
			return null;

		if (bufferLength == 0)
			return new ArrayList<String>(0);

		Memory valueBuffer = new Memory(bufferLength);
		long valueLength = XAttr.INSTANCE.listxattr(path, valueBuffer, bufferLength, 0);

		if (valueLength < 0)
			return null;

		return decodeStringSequence(valueBuffer.getByteBuffer(0, valueLength));
	}

	public static String getXAttr(String path, String name) {
		// get required buffer size
		long bufferLength = XAttr.INSTANCE.getxattr(path, name, null, 0, 0, 0);

		if (bufferLength < 0)
			return null;

		Memory valueBuffer = new Memory(bufferLength);
		long valueLength = XAttr.INSTANCE.getxattr(path, name, valueBuffer, bufferLength, 0, 0);

		if (valueLength < 0)
			return null;

		return decodeString(valueBuffer.getByteBuffer(0, valueLength));
	}

	public static int setXAttr(String path, String name, String value) {
		Memory valueBuffer = encodeString(value);
		return XAttr.INSTANCE.setxattr(path, name, valueBuffer, valueBuffer.size(), 0, 0);
	}

	public static int removeXAttr(String path, String name) {
		return XAttr.INSTANCE.removexattr(path, name, 0);
	}

	protected static Memory encodeString(String s) {
		byte[] bytes = s.getBytes(UTF_8);
		Memory valueBuffer = new Memory(bytes.length);
		valueBuffer.write(0, bytes, 0, bytes.length);
		return valueBuffer;
	}

	protected static String decodeString(ByteBuffer bb) {
		// handle null-terminated String values gracefully
		return UTF_8.decode(bb).codePoints().takeWhile(c -> c != 0).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
	}

	protected static List<String> decodeStringSequence(ByteBuffer bb) {
		List<String> names = new ArrayList<String>();

		// first key starts from here
		bb.mark();

		while (bb.hasRemaining()) {
			if (bb.get() == 0) {
				ByteBuffer nameBuffer = (ByteBuffer) bb.duplicate().limit(bb.position() - 1).reset();
				if (nameBuffer.hasRemaining()) {
					names.add(decodeString(nameBuffer));
				}

				// next key starts from here
				bb.mark();
			}
		}

		return names;
	}

}
