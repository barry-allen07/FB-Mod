package net.filebot.web;

public class MoviePart extends Movie {

	protected int partIndex;
	protected int partCount;

	public MoviePart() {
		// used by deserializer
	}

	public MoviePart(MoviePart obj) {
		this(obj, obj.partIndex, obj.partCount);
	}

	public MoviePart(Movie movie, int partIndex, int partCount) {
		super(movie.getName(), movie.getAliasNames(), movie.getYear(), movie.getImdbId(), movie.getTmdbId(), movie.getLanguage());
		this.partIndex = partIndex;
		this.partCount = partCount;
	}

	public int getPartIndex() {
		return partIndex;
	}

	public int getPartCount() {
		return partCount;
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof MoviePart && super.equals(object)) {
			MoviePart other = (MoviePart) object;
			return partIndex == other.partIndex && partCount == other.partCount;
		}

		return super.equals(object);
	}

	@Override
	public MoviePart clone() {
		return new MoviePart(this);
	}

	@Override
	public String toString() {
		return String.format("%s (%d) [CD%d]", name, year, partIndex);
	}

}
