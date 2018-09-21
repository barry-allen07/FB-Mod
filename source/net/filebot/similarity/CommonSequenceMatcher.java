package net.filebot.similarity;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static net.filebot.util.RegularExpressions.*;

import java.text.CollationKey;
import java.text.Collator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CommonSequenceMatcher {

	public static Collator getLenientCollator(Locale locale) {
		// use maximum strength collator by default
		Collator collator = Collator.getInstance(locale);
		collator.setDecomposition(Collator.FULL_DECOMPOSITION);
		collator.setStrength(Collator.PRIMARY);
		return collator;
	}

	protected final Collator collator;
	protected final int commonSequenceMaxStartIndex;
	protected final boolean returnFirstMatch;

	public CommonSequenceMatcher(Collator collator, int commonSequenceMaxStartIndex, boolean returnFirstMatch) {
		this.collator = collator;
		this.commonSequenceMaxStartIndex = commonSequenceMaxStartIndex;
		this.returnFirstMatch = returnFirstMatch;
	}

	public Collator getCollator() {
		return collator;
	}

	public String matchFirstCommonSequence(String... names) {
		CollationKey[][] words = new CollationKey[names.length][];
		for (int i = 0; i < names.length; i++) {
			words[i] = split(names[i]);
		}
		return synth(matchFirstCommonSequence(words));
	}

	public <E extends Comparable<E>> E[] matchFirstCommonSequence(E[][] names) {
		E[] common = null;

		for (E[] words : names) {
			if (common == null) {
				// initialize common with current word array
				common = words;
			} else {
				// find common sequence
				common = firstCommonSequence(common, words, commonSequenceMaxStartIndex, returnFirstMatch);

				if (common == null) {
					// no common sequence
					return null;
				}
			}
		}
		return common;
	}

	protected String synth(CollationKey[] keys) {
		if (keys == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (CollationKey it : keys) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(it.getSourceString());
		}
		return sb.toString();
	}

	public CollationKey[] split(String sequence) {
		return getCollationKeys(SPACE.split(sequence));
	}

	private final Map<String, CollationKey> collationKeyDictionary = synchronizedMap(new HashMap<String, CollationKey>(256));

	protected CollationKey[] getCollationKeys(String[] words) {
		CollationKey[] keys = new CollationKey[words.length];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = collationKeyDictionary.get(words[i]);
			if (keys[i] == null) {
				keys[i] = collator.getCollationKey(words[i]);
				collationKeyDictionary.put(words[i], keys[i]);
			}
		}
		return keys;
	}

	protected <E extends Comparable<E>> E[] firstCommonSequence(E[] seq1, E[] seq2, int maxStartIndex, boolean returnFirstMatch) {
		E[] matchSeq = null;
		for (int i = 0; i < seq1.length && i <= maxStartIndex; i++) {
			for (int j = 0; j < seq2.length && j <= maxStartIndex; j++) {
				// common sequence length
				int len = 0;

				// iterate over common sequence
				while ((i + len < seq1.length) && (j + len < seq2.length) && (seq1[i + len].compareTo(seq2[j + len]) == 0)) {
					len++;
				}

				// check if a common sequence was found
				if (len > (matchSeq == null ? 0 : matchSeq.length)) {
					matchSeq = copyOfRange(seq1, i, i + len);

					// look for first match
					if (returnFirstMatch) {
						return matchSeq;
					}
				}
			}
		}
		return matchSeq;
	}
}
