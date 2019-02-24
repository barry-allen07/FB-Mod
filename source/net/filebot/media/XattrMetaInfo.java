package net.filebot.media;

import static net.filebot.Logging.*;
import static net.filebot.Settings.*;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.filebot.Resource;
import net.filebot.WebServices;
import net.filebot.web.Episode;
import net.filebot.web.Movie;
import net.filebot.web.SimpleDate;

public class XattrMetaInfo {

	public static final XattrMetaInfo xattr = new XattrMetaInfo(useExtendedFileAttributes(), useCreationDate());

	private final boolean useExtendedFileAttributes;
	private final boolean useCreationDate;

	private final Cache<File, Optional<Object>> xattrMetaInfoCache = Caffeine.newBuilder().expireAfterAccess(24, TimeUnit.HOURS).build();
	private final Cache<File, Optional<Object>> xattrOriginalNameCache = Caffeine.newBuilder().expireAfterAccess(24, TimeUnit.HOURS).build();

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

	private Object getXattrValue(Cache<File, Optional<Object>> cache, File file, Function<MetaAttributes, Object> compute) {
		// try in-memory cache of previously stored xattr metadata
		if (!useExtendedFileAttributes) {
			return cache.getIfPresent(file);
		}

		return cache.get(file, f -> {
			try {
				return Optional.ofNullable(compute.apply(xattr(f)));
			} catch (Exception e) {
				debug.warning(cause("Failed to read xattr", e));
				return Optional.empty();
			}
		}).orElse(null);// read only
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
				xattrMetaInfoCache.put(file, Optional.of(model));

				if (useExtendedFileAttributes) {
					xattr.get().setObject(model);
				}
			}

			if (original != null && original.length() > 0 && getOriginalName(file) == null) {
				xattrOriginalNameCache.put(file, Optional.of(original));

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
		xattrMetaInfoCache.invalidate(file);
		xattrOriginalNameCache.invalidate(file);

		if (useExtendedFileAttributes) {
			try {
				xattr(writable(file)).clear();
			} catch (Throwable e) {
				debug.warning(cause("Failed to clear xattr", e));
			}
		}
	}

}
