package net.filebot.media;

import static java.util.ResourceBundle.*;
import static net.filebot.util.RegularExpressions.*;

public class VideoFormat {

	public static final VideoFormat DEFAULT_GROUPS = new VideoFormat();

	private final int[] ws;
	private final int[] hs;

	public VideoFormat() {
		this.ws = getIntArrayProperty("resolution.steps.w");
		this.hs = getIntArrayProperty("resolution.steps.h");
	}

	public int guessFormat(int width, int height) {
		int ns = 0;

		for (int i = 0; i < ws.length - 1; i++) {
			if ((width >= ws[i] || height >= hs[i]) || (width > ws[i + 1] && height > hs[i + 1])) {
				ns = hs[i];
				break;
			}
		}

		if (ns > 0) {
			return ns;
		}

		throw new IllegalArgumentException(String.format("Illegal resolution: [%d, %d]", width, height));
	}

	private int[] getIntArrayProperty(String key) {
		return SPACE.splitAsStream(getBundle(getClass().getName()).getString(key)).mapToInt(Integer::parseInt).toArray();
	}

}
