#!/usr/bin/env filebot -script

import java.util.regex.*
import org.tukaani.xz.*


// ------------------------------ UPDATE LISTS ------------------------------ //


def dir_root    = project as File
def dir_data    = data as File
def dir_release = release as File


// sort and check shared regex collections
def dir_data_master = System.getProperty('net.filebot.data.master', 'https://raw.githubusercontent.com/filebot/data/master')

['add-series-alias.txt', 
 'query-blacklist.txt', 
 'release-groups.txt', 
 'series-mappings.txt'
].each{
	def input = new URL(dir_data_master + '/' + it)
	def output = dir_release.resolve(it)

	log.finest "Fetch $input"
	def lines = new TreeSet(String.CASE_INSENSITIVE_ORDER)
	input.getText('UTF-8').split(/\R/)*.trim().findAll{ it.length() > 0 }.each{
		lines << Pattern.compile(it).pattern()
	}

	lines.each{ log.finest "$it" }
	pack(output, lines)
}


// ------------------------------ FUNCTIONS ------------------------------ //


def moviedb_out = dir_release.resolve('moviedb.txt')
def thetvdb_out = dir_release.resolve('thetvdb.txt')
def anidb_out   = dir_release.resolve('anidb.txt')
def osdb_out    = dir_release.resolve('osdb.txt')


def pack(file, lines) {
	file.withOutputStream{ out ->
		out.withWriter('UTF-8'){ writer ->
			lines.each{ writer.append(it).append('\n') }
		}
	}
	file.parentFile.resolve(file.name + '.xz').withOutputStream{ out ->
		new XZOutputStream(out, new LZMA2Options(LZMA2Options.PRESET_DEFAULT)).withWriter('UTF-8'){ writer ->
			lines.each{ writer.append(it).append('\n') }
		}
	}
	def rows = lines.size()
	def columns = lines.collect{ it.split(/\t/).length }.max()
	log.info "${file.canonicalFile} ($rows rows, $columns columns)"
}


def isValidMovieName(s) {
	return (s.normalizePunctuation().length() >= 4) || (s=~ /^[A-Z0-9]/ && s =~ /[\p{Alnum}]{3}/)
}

def getNamePermutations(names) {
	def normalize = { s -> s.toLowerCase().normalizePunctuation() }.memoize()

	def out = names*.trim().unique().collectMany{ original ->
		def s = original.trim()
		s = s.replaceAll(/([,]\s(The|A)$)/, '')
		s = s.replaceAll(/\s&\s/, ' and ')
		s = s.replaceAll(/\s\([^\)]*\)$/, '').trim()
		s = s.replaceAll(/^(?i:The|A)\s/, '').trim()
		return [original, s]
	}.unique{ normalize(it) }.findAll{ it.length() > 0 }

	out = out.findAll{ it.length() >= 2 && !(it ==~ /[1][0-9][1-9]/) && it =~ /^[@.\p{L}\p{Digit}]/ } // MUST START WITH UNICODE LETTER
	out = out.findAll{ !MediaDetection.releaseInfo.structureRootPattern.matcher(it).matches() } // IGNORE NAMES THAT OVERLAP WITH MEDIA FOLDER NAMES

	return out
}

def treeSort(list, keyFunction) {
	def sorter = new TreeMap(String.CASE_INSENSITIVE_ORDER)
	list.each{
		sorter.put(keyFunction(it), it)
	}
	return sorter.values()
}

def csv(f, delim, keyIndex, valueIndex) {
	def values = [:]
	if (f.isFile()) {
		f.splitEachLine(delim, 'UTF-8') { line ->
			values.put(line[keyIndex], tryQuietly{ line[valueIndex] })
		}
	}
	return values
}


// ------------------------------ BUILD MOVIE INDEX ------------------------------ //


if (_args.mode == /no-index/) {
	return
}


def movies_index = [:]

['ancient-movies.txt', 'recent-movies.txt'].each{
	movies_index << csv(dir_data.resolve(it), '\t', 1, [1..-1])
}

def tmdb_txt = dir_data.resolve('tmdb.txt')
def tmdb_index = csv(tmdb_txt, '\t', 1, [0..-1])

def tmdb = []
movies_index.values().each{ m ->
	def sync = System.currentTimeMillis()
	if (tmdb_index.containsKey(m[0]) && (sync - tmdb_index[m[0]][0].toLong()) < ((m[1].toInteger() < 2000 ? 360 : 120) * 24 * 60 * 60 * 1000L) ) {
		tmdb << tmdb_index[m[0]]
		return
	}
	try {
		def info = WebServices.TheMovieDB.getMovieInfo("tt${m[0]}", Locale.ENGLISH, true)

		if (info.votes <= 1 || info.rating <= 2) {
			throw new IllegalArgumentException('Movie not popular enough: ' + info)
		}

		def names = [info.name, info.originalName] + info.alternativeTitles
		[info?.released?.year, m[1]].findResults{ it?.toInteger() }.unique().each{ y ->
			def row = [sync, m[0].pad(7), info.id.pad(7), y.pad(4)] + names
			log.info "Update ${m[0..2]}: $row"
			tmdb << row
		}
	} catch(IllegalArgumentException | FileNotFoundException e) {
		printException(e)
		def row = [sync, m[0].pad(7), 0, m[1], m[2]]
		log.info "[BAD] Update $m: $row"
		tmdb << row
	}
}

tmdb*.join('\t').join('\n').saveAs(tmdb_txt)


def movies = tmdb.findResults{
	def ity = it[1..3] // imdb id, tmdb id, year
	def names = getNamePermutations(it[4..-1]).findAll{ isValidMovieName(it) }
	if (ity[0].toInteger() > 0 && ity[1].toInteger() > 0 && names.size() > 0)
		return ity + names
	else
		return null
}
movies = treeSort(movies, { it[3, 2].join(' ') })

// sanity check
if (movies.size() < 20000) { die('Movie index sanity failed:' + movies.size()) }
pack(moviedb_out, movies*.join('\t'))


// ------------------------------ BUILD SERIES INDEX ------------------------------ //


def tvdb_txt = dir_data.resolve('tvdb.txt')
def tvdb = [:]

if (tvdb_txt.exists()) {
	tvdb_txt.eachLine('UTF-8'){
		def line = it.split('\t') as List

		tvdb.put(line[1] as Integer, [line[0] as Long, line[1] as Integer, line[2], line[3] as Float, line[4] as Float, line[5] as Integer] + line[6..<line.size()])
	}
}

def tvdb_updates = [:] as TreeMap
dir_data.resolve('updates_all.xml').eachLine('UTF-8'){
	def m = (it =~ '<Series><id>(\\d+)</id><time>(\\d+)</time></Series>')
	while(m.find()) {
		def id = m.group(1) as Integer
		def time = m.group(2) as Integer
		tvdb_updates[id] = [id: id, time: time]
	}
}

// blacklist crap entries
tvdb_updates.remove(219901)
tvdb_updates.remove(256135)


tvdb_updates.values().each{ update ->
	if (tvdb[update.id] == null || update.time > tvdb[update.id][0]) {
		try {
			retry(2, 60000) {
				def seriesNames = []

				def seriesInfo = TheTVDB.getSeriesInfo(update.id, Locale.ENGLISH)
				def imdbid = seriesInfo.imdbId ?: ''

				seriesNames += seriesInfo.name
				seriesNames += seriesInfo.aliasNames

				def rating = seriesInfo.rating ?: 0
				def votes = seriesInfo.ratingCount ?: 0
				def year = seriesInfo.startDate?.year ?: 0

				if (imdbid) tryLogCatch {
					def omdbInfo = OMDb.getMovieInfo(new Movie(imdbid.match(/tt(\d+)/) as int))

					seriesNames += omdbInfo.name
					seriesNames += omdbInfo.originalName
					seriesNames += omdbInfo.alternativeTitles

					if (omdbInfo.votes && omdbInfo.rating) {
						votes = omdbInfo.votes as int
						rating = omdbInfo.rating as double

						// scrape extra alias titles from webpage (not supported yet by API yet)
						if (votes >= 60 && rating >= 4) {
							def jsoup = org.jsoup.Jsoup.connect("https://www.thetvdb.com/series/${seriesInfo.slug}").get()
							def intlseries = jsoup.select('#translations div.change_translation_text')*.attr('data-title')*.trim()
							log.fine "Scraped data $intlseries for series $seriesNames"
							seriesNames += intlseries
						}
					}
				}

				def data = [update.time, update.id, imdbid, rating, votes, year] + seriesNames.findAll{ it != null && it.length() > 0 }
				log.info "Update $update => $data"

				tvdb.put(update.id, data)
			}
		}
		catch(Throwable e) {
			printException(e)
			def data = [update.time, update.id, '', 0, 0, 0]
			tvdb.put(update.id, data)
			log.info "[BAD] Update $update => $data"
		}
	}
}

// remove entries that have become invalid
tvdb.keySet().toList().each{ id ->
	if (tvdb_updates[id] == null) {
		log.finest "Invalid ID found: ${tvdb[id]}"
		tvdb.remove(id)
	}
}
tvdb.values().findResults{ it.collect{ it.toString().replace('\t', '').trim() }.join('\t') }.join('\n').saveAs(tvdb_txt)

// additional custom mappings
def extraAliasNames = csv(dir_release.resolve('add-series-alias.txt'), '\t', 0, [1..-1])

def thetvdb_index = []
tvdb.values().each{ r ->
	def tvdb_id = r[1]
	def rating = r[3]
	def votes = r[4]
	def year = r[5]
	def names = r[6..<r.size()]

	// ignore invalid entries
	if (names.isEmpty()) {
		return
	}

	if (year > 0) {
		names.add(1, names[0].replaceTrailingBrackets() + " ($year)")
	}

	def alias = extraAliasNames[names[0]]
	if (alias) {
		log.finest "Add alias ${names[0]} => ${alias}"
		names += alias
	}

	// always include if alias has been manually added
	if (alias != null || (votes >= 5 && rating >= 4) || (votes >= 2 && rating >= 6) || (votes >= 1 && rating >= 10)) {
		getNamePermutations(names).each{ n ->
			thetvdb_index << [tvdb_id, n]
		}
	}
}

thetvdb_index = thetvdb_index.findResults{ [it[0] as Integer, it[1].replaceAll(/\s+/, ' ').trim()] }.findAll{ !(it[1] =~ /(?i:Duplicate|Series.Not.Permitted|Episode.#\d+.\d+)/ || it[1] =~ /\d{6,}/ || it[1].startsWith('*') || it[1].endsWith('*') || it[1].length() < 2) }
thetvdb_index = thetvdb_index.sort{ a, b -> a[0] <=> b[0] }

// join and sort
def thetvdb_txt = thetvdb_index.groupBy{ it[0] }.findResults{ k, v -> ([k.pad(6)] + v*.getAt(1).unique{ it.toLowerCase() }).join('\t') }

// sanity check
if (thetvdb_txt.size() < 4000) { die('TheTVDB index sanity failed: ' + thetvdb_txt.size()) }
pack(thetvdb_out, thetvdb_txt)


// ------------------------------ BUILD OSDB INDEX ------------------------------ //


def osdb = []

dir_data.resolve('osdb.txt').eachLine('UTF-8'){
	def fields = it.split(/\t/)*.trim()

	// 0 IDMovie, 1 IDMovieImdb, 2 MovieName, 3 MovieYear, 4 MovieKind, 5 MoviePriority
	if (fields.size() == 6 && fields[1] ==~ /\d+/ && fields[3] ==~ /\d{4}/) {
		if (fields[4] ==~ /movie|tv.series/ && isValidMovieName(fields[2]) && (fields[3] as int) >= 1970 && (fields[5] as int) >= 500) {
			// 0 imdbid, 1 name, 2 year, 3 kind, 4 priority
			osdb << [fields[1] as int, fields[2], fields[3] as int, fields[4] == /movie/ ? 'm' : fields[4] == /tv series/ ? 's' : '?', fields[5] as int]
		}
	}
}

// sort reverse by score
osdb.sort{ a, b -> b[4] <=> a[4] }

// reset score/priority because it's currently not used
osdb*.set(4, 0)

// map by imdbid
def tvdb_index = tvdb.values().findAll{ it[2] =~ /tt(\d+)/ }.collectEntries{ [it[2].substring(2).pad(7), it] }

// collect final output data
osdb = osdb.findResults{
	def names = [it[1]]
	if (it[3] == 'm') {
		def tmdb_entry = tmdb_index[it[0].pad(7)]
		if (tmdb_entry != null && tmdb_entry.size() > 4) {
			names += tmdb_entry[4..-1]
		}
	} else if (it[3] == 's') {
		def tvdb_entry = tvdb_index[it[0].pad(7)]
		if (tvdb_entry != null && tvdb_entry.size() > 5) {
			names += tvdb_entry[5..-1]
		}
	}
	// 0 kind, 1 score, 2 imdbid, 3 year, 4-n names
	return [it[3], it[4], it[0], it[2]] + names.unique()
}

// sanity check
if (osdb.size() < 15000) { die('OSDB index sanity failed:' + osdb.size()) }
pack(osdb_out, osdb*.join('\t'))


// ------------------------------ BUILD ANIDB INDEX ------------------------------ //


def anidb = new AnidbClient('filebot', 6).getAnimeTitles() as List
def animeExcludes = [] as Set

// exclude anime movies from anime index
dir_data.resolve('anime-list.xml').eachLine('UTF-8') {
    if (it =~ /tvdbid="movie"/ || it =~ /imdbid="ttd\+"/) {
        animeExcludes << it.match(/anidbid="(\d+)"/).toInteger()
    }
}

def anidb_index = anidb.findResults{
	if (animeExcludes.contains(it.id))
		return null

	def names = it.effectiveNames*.replaceAll(/\s+/, ' ')*.trim()*.replaceAll(/['`´‘’ʻ]+/, /'/)
	names = getNamePermutations(names)

	return names.empty ? null : [it.id.pad(5)] + names.take(4)
}

// join and sort
def anidb_txt = anidb_index.findResults{ row -> row.join('\t') }.sort().unique()

// sanity check
if (anidb_txt.size() < 8000 || animeExcludes.size() < 500) { die('AniDB index sanity failed:' + anidb_txt.size()) }
pack(anidb_out, anidb_txt)
