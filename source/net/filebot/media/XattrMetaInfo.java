package net.filebot.media;

import static net.filebot.Logging.*;
import static net.filebot.Settings.*;

import java.io.File;
import java.util.Locale;
import java.util.function.Function;

import net.filebot.Cache;
import net.filebot.CacheType;
import net.filebot.Resource;
import net.filebot.WebServices;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.SimpleDate;

public class XattrMetaInfo {

	public static final XattrMetaInfo xattr = new XattrMetaInfo(useExtendedFileAttributes(), useCreationDate());

	private final boolean useExtendedFileAttributes;
	private final boolean useCreationDate;

	private final Cache xattrMetaInfoCache = Cache.getCache(MetaAttributes.METADATA_KEY, CacheType.Ephemeral);
	private final Cache xattrOriginalNameCache = Cache.getCache(MetaAttributes.FILENAME_KEY, CacheType.Ephemeral);

	public XattrMetaInfo(boolean useExtendedFileAttributes, boolean useCreationDate) {
		this.useExtendedFileAttributes = useExtendedFileAttributes;
		this.useCreationDate = useCreationDate;
	}

	public boolean isMetaInfo(Object object) {
		return object instanceof Episode || object instanceof Movie;
	}

	public long getTimeStamp(Object object) throws Exception {
		if (object instanceof Episode) {
			Episode episode = (Episode) object;
			if (episode.getAirdate() != null) {
				return episode.getAirdate().getTimeStamp();
			}
		} else if (object instanceof Movie) {
			Movie movie = (Movie) object;
			if (movie.getYear() > 0 && movie.getTmdbId() > 0) {
				SimpleDate releaseDate = WebServices.TheMovieDB.getMovieInfo(movie, Locale.US, false).getReleased();
				if (releaseDate != null) {
					return releaseDate.getTimeStamp();
				}
			}
		}
		return -1;
	}

	public synchronized Object getMetaInfo(File file) {
		return getXattrValue(xattrMetaInfoCache, file, MetaAttributes::getObject);
	}

	public synchronized String getOriginalName(File file) {
		return (String) getXattrValue(xattrOriginalNameCache, file, MetaAttributes::getOriginalName);
	}

	private Object getXattrValue(Cache cache, File file, Function<MetaAttributes, Object> compute) {
		// try in-memory cache of previously stored xattr metadata
		if (!useExtendedFileAttributes) {
			return cache.get(file);
		}

		try {
			return cache.computeIfAbsent(file, element -> compute.apply(xattr(file))); // read only
		} catch (Throwable e) {
			debug.warning(cause("Failed to read xattr", e));
		}
		return null;
	}

	private File writable(File f) throws Exception {
		// make file writable if necessary
		if (!f.canWrite()) {
			if (f.setWritable(true)) {
				debug.fine(message("Grant write permissions", f));
			} else {
				debug.warning(message("Failed to grant write permissions", f));
			}
		}
		return f;
	}

	private MetaAttributes xattr(File f) throws Exception {
		return new MetaAttributes(f);
	}

	public synchronized void setMetaInfo(File file, Object model, String original) {
		// only for Episode / Movie objects
		if (!isMetaInfo(model) || !file.isFile()) {
			return;
		}

		// set creation date to episode / movie release date
		Resource<MetaAttributes> xattr = Resource.lazy(() -> xattr(writable(file)));

		if (useCreationDate) {
			try {
				long t = getTimeStamp(model);
				if (t > 0) {
					xattr.get().setCreationDate(t);
				}
			} catch (Throwable e) {
				debug.warning(cause("Failed to set creation date", e));
			}
		}

		// store metadata object and original name as xattr
		try {
			if (isMetaInfo(model)) {
				xattrMetaInfoCache.put(file, model);

				if (useExtendedFileAttributes) {
					xattr.get().setObject(model);
				}
			}

			if (original != null && original.length() > 0 && getOriginalName(file) == null) {
				xattrOriginalNameCache.put(file, original);

				if (useExtendedFileAttributes) {
					xattr.get().setOriginalName(original);
				}
			}
		} catch (Throwable e) {
			debug.warning(cause("Failed to set xattr", e));
		}
	}

	public synchronized void clear(File file) {
		// clear in-memory cache
		xattrMetaInfoCache.remove(file);
		xattrOriginalNameCache.remove(file);

		if (useExtendedFileAttributes) {
			try {
				xattr(writable(file)).clear();
			} catch (Throwable e) {
				debug.warning(cause("Failed to clear xattr", e));
			}
		}
	}

}
