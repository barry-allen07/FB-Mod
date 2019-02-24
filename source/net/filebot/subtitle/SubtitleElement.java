
package net.filebot.subtitle;

public class SubtitleElement {

	private final long start;
	private final long end;

	private final String text;

	public SubtitleElement(long start, long end, String text) {
		this.start = start;
		this.end = end;
		this.text = text;
	}

	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}

	public String getText() {
		return text;
	}

	public boolean isEmpty() {
		return start >= end || text.isEmpty();
	}

	@Override
	public String toString() {
		return String.format("[%d, %d] %s", start, end, text);
	}

}
