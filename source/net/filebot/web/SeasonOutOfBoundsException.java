
package net.filebot.web;


public class SeasonOutOfBoundsException extends IndexOutOfBoundsException {

	private final String seriesName;
	private final int season;
	private final int lastSeason;


	public SeasonOutOfBoundsException(String seriesName, int season, int lastSeason) {
		this.seriesName = seriesName;
		this.season = season;
		this.lastSeason = lastSeason;
	}


	@Override
	public String getMessage() {
		return String.format("%s has only %d season%s.", seriesName, lastSeason, lastSeason != 1 ? "s" : "");
	}


	public String getSeriesName() {
		return seriesName;
	}


	public int getSeason() {
		return season;
	}


	public int getLastSeason() {
		return lastSeason;
	}

}
