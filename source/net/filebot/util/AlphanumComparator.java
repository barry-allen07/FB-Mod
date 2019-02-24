package net.filebot.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

public class AlphanumComparator implements Comparator<String> {

	protected Collator collator;

	public AlphanumComparator(Collator collator) {
		this.collator = collator;
	}

	public AlphanumComparator(Locale locale) {
		this.collator = Collator.getInstance(locale);
		this.collator.setDecomposition(Collator.FULL_DECOMPOSITION);
		this.collator.setStrength(Collator.PRIMARY);
	}

	protected boolean isDigit(String s, int i) {
		return Character.isDigit(s.charAt(i));
	}

	protected int getNumericValue(String s, int i) {
		return Character.getNumericValue(s.charAt(i));
	}

	protected String getChunk(String s, int start) {
		int index = start;
		int length = s.length();
		boolean mode = isDigit(s, index++);

		while (index < length) {
			if (mode != isDigit(s, index)) {
				break;
			}

			++index;
		}

		return s.substring(start, index);
	}

	public int compare(String s1, String s2) {
		int length1 = s1.length();
		int length2 = s2.length();
		int index1 = 0;
		int index2 = 0;
		int result = 0;

		while (result == 0 && index1 < length1 && index2 < length2) {
			String chunk1 = getChunk(s1, index1);
			index1 += chunk1.length();

			String chunk2 = getChunk(s2, index2);
			index2 += chunk2.length();

			if (isDigit(chunk1, 0) && isDigit(chunk2, 0)) {
				int chunkLength1 = chunk1.length();
				int chunkLength2 = chunk2.length();

				// count and skip leading zeros
				int zeroIndex1 = 0;
				while (zeroIndex1 < chunkLength1 && getNumericValue(chunk1, zeroIndex1) == 0) {
					++zeroIndex1;
				}

				// count and skip leading zeros
				int zeroIndex2 = 0;
				while (zeroIndex2 < chunkLength2 && getNumericValue(chunk2, zeroIndex2) == 0) {
					++zeroIndex2;
				}

				// the longer run of non-zero digits is greater
				result = (chunkLength1 - zeroIndex1) - (chunkLength2 - zeroIndex2);

				// if the length is the same, the first differing digit decides
				// which one is deemed greater.
				int numberIndex1 = zeroIndex1;
				int numberIndex2 = zeroIndex2;

				while (result == 0 && numberIndex1 < chunkLength1 && numberIndex2 < chunkLength2) {
					result = getNumericValue(chunk1, numberIndex1++) - getNumericValue(chunk2, numberIndex2++);
				}

				// if still no difference, the longer zeros-prefix is greater
				if (result == 0) {
					result = numberIndex1 - numberIndex2;
				}
			} else {
				result = collator.compare(chunk1, chunk2);
			}
		}

		// if there was no difference at all, let the longer one be the greater one
		if (result == 0) {
			result = length1 - length2;
		}

		// limit result to (-1, 0, or 1)
		return Integer.signum(result);
	}

}