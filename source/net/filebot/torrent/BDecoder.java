/*
 * BeDecoder.java
 *
 * Created on May 30, 2003, 2:44 PM
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package net.filebot.torrent;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of utility methods to decode a bencoded array of byte into a Map. integer are represented as Long, String as byte[], dictionnaries as Map, and list as List.
 *
 * @author TdC_VgA
 */
class BDecoder {

	public static Map<?, ?> decode(InputStream is) throws IOException {
		return (new BDecoder().decodeStream(is));
	}

	public Map<?, ?> decodeStream(InputStream data) throws IOException {
		Object res = decodeInputStream(data, 0);

		if (res == null)
			throw (new IOException("BDecoder: zero length file"));
		else if (!(res instanceof Map))
			throw (new IOException("BDecoder: top level isn't a Map"));

		return ((Map<?, ?>) res);
	}

	private Object decodeInputStream(InputStream bais, int nesting) throws IOException {
		if (!bais.markSupported())
			throw new IOException("InputStream must support the mark() method");

		// set a mark
		bais.mark(Integer.MAX_VALUE);

		// read a byte
		int tempByte = bais.read();

		// decide what to do
		switch (tempByte) {
		case 'd':
			// create a new dictionary object
			Map<String, Object> tempMap = new HashMap<String, Object>();

			// get the key
			byte[] tempByteArray = null;

			while ((tempByteArray = (byte[]) decodeInputStream(bais, nesting + 1)) != null) {

				// decode some more

				Object value = decodeInputStream(bais, nesting + 1);

				// add the value to the map

				CharBuffer cb = ISO_8859_1.decode(ByteBuffer.wrap(tempByteArray));

				String key = new String(cb.array(), 0, cb.limit());

				tempMap.put(key, value);
			}

			if (bais.available() < nesting)
				throw (new IOException("BDecoder: invalid input data, 'e' missing from end of dictionary"));

			// return the map
			return tempMap;

		case 'l':
			// create the list
			List<Object> tempList = new ArrayList<Object>();

			// create the key
			Object tempElement = null;
			while ((tempElement = decodeInputStream(bais, nesting + 1)) != null)
				// add the element
				tempList.add(tempElement);

			if (bais.available() < nesting)
				throw (new IOException("BDecoder: invalid input data, 'e' missing from end of list"));

			// return the list
			return tempList;

		case 'e':
		case -1:
			return null;

		case 'i':
			return getNumberFromStream(bais, 'e');

		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			// move back one
			bais.reset();
			// get the string
			return getByteArrayFromStream(bais);

		default: {

			int rem_len = bais.available();

			if (rem_len > 256)
				rem_len = 256;

			byte[] rem_data = new byte[rem_len];

			bais.read(rem_data);

			throw (new IOException("BDecoder: unknown command '" + tempByte + ", remainder = " + new String(rem_data)));
		}
		}
	}

	private long getNumberFromStream(InputStream bais, char parseChar) throws IOException {
		int length = 0;

		// place a mark
		bais.mark(Integer.MAX_VALUE);

		int tempByte = bais.read();
		while ((tempByte != parseChar) && (tempByte >= 0)) {
			tempByte = bais.read();
			length++;
		}

		// are we at the end of the stream?
		if (tempByte < 0)
			return -1;

		// reset the mark
		bais.reset();

		// get the length
		byte[] tempArray = new byte[length];
		int count = 0;
		int len = 0;

		// get the string
		while ((count != length) && ((len = bais.read(tempArray, count, length - count)) > 0))
			count += len;

		// jump ahead in the stream to compensate for the :
		bais.skip(1);

		// return the value
		CharBuffer cb = ISO_8859_1.decode(ByteBuffer.wrap(tempArray));

		String str_value = new String(cb.array(), 0, cb.limit());

		return Long.parseLong(str_value);
	}

	private byte[] getByteArrayFromStream(InputStream bais) throws IOException {
		int length = (int) getNumberFromStream(bais, ':');

		if (length < 0)
			return null;

		// note that torrent hashes can be big (consider a 55GB file with 2MB
		// pieces
		// this generates a pieces hash of 1/2 meg
		if (length > 8 * 1024 * 1024)
			throw (new IOException("Byte array length too large (" + length + ")"));

		byte[] tempArray = new byte[length];
		int count = 0;
		int len = 0;

		// get the string
		while ((count != length) && ((len = bais.read(tempArray, count, length - count)) > 0))
			count += len;

		if (count != tempArray.length)
			throw (new IOException("BDecoder::getByteArrayFromStream: truncated"));

		return tempArray;
	}

}
