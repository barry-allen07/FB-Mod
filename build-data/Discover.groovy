#!/usr/bin/env filebot -script

def recentMoviesFile = new File('recent-movies.txt')
def recentMoviesIndex = new TreeMap()

if (recentMoviesFile.exists()) {
	recentMoviesFile.splitEachLine('\t', 'UTF-8') { line ->
		recentMoviesIndex.put(line[0] as int, line)
	}
}

def updateMovieIndex = { m ->
	if (!recentMoviesIndex.containsKey(m.tmdbId)) {
		def i = TheMovieDB.getMovieInfo(m, Locale.ENGLISH, false)

		if (i == null || i.imdbId == null)
			return

		def row = [i.id.pad(6), i.imdbId.pad(7), i.released.year as String, i.name]
		log.finest "$row"

		recentMoviesIndex.put(row[0] as int, row)
	}
}


def to = LocalDate.now()
def from = to.minus(Period.ofDays(30))
def year = from.year

log.fine "Discover Recent [$from to $to]"
TheMovieDB.discover(from, to, Locale.ENGLISH).each{ updateMovieIndex(it) }

log.fine "Discover Best of Year [$year]"
TheMovieDB.discover(year, Locale.ENGLISH).each{ updateMovieIndex(it) }


recentMoviesIndex.values()*.join('\t').join('\n').saveAs(recentMoviesFile)
