
package net.filebot.similarity;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.temporal.ChronoUnit;

public class TimeStampMetric implements SimilarityMetric {

	private long epoch;

	public TimeStampMetric(int i, ChronoUnit unit) {
		this.epoch = unit.getDuration().multipliedBy(i).toMillis();
	}

	@Override
	public float getSimilarity(Object o1, Object o2) {
		long t1 = getTimeStamp(o1);
		long t2 = getTimeStamp(o2);

		if (t1 > 0 && t2 > 0) {
			float delta = Math.abs(t1 - t2);

			return delta > epoch ? 0 : 1 - (delta / epoch);
		}

		return -1;
	}

	public long getTimeStamp(Object object) {
		if (object instanceof File) {
			File f = (File) object;
			try {
				BasicFileAttributes attr = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
				long creationTime = attr.creationTime().toMillis();
				if (creationTime > 0) {
					return creationTime;
				} else {
					return attr.lastModifiedTime().toMillis();
				}
			} catch (Exception e) {
				// ignore, default to -1
			}
		}

		return -1;
	}
}
