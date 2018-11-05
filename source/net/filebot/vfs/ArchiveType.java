package net.filebot.vfs;

import static java.util.Collections.*;
import static net.filebot.Logging.*;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.logging.Level;

public enum ArchiveType {

	ZIP {

		@Override
		public Iterable<MemoryFile> fromData(ByteBuffer data) {
			return new ZipArchive(data);
		}
	},

	UNDEFINED {

		@Override
		public Iterable<MemoryFile> fromData(ByteBuffer data) {
			for (ArchiveType type : EnumSet.of(ZIP)) {
				try {
					Iterable<MemoryFile> files = type.fromData(data);
					if (files.iterator().hasNext()) {
						return files;
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, e, e::toString);
				}
			}

			// cannot extract data, return empty archive
			return emptySet();
		}
	},

	UNKOWN {

		@Override
		public Iterable<MemoryFile> fromData(ByteBuffer data) {
			// cannot extract data, return empty archive
			return emptySet();
		}
	};

	public abstract Iterable<MemoryFile> fromData(ByteBuffer data);

	public static ArchiveType forName(String name) {
		if (name == null)
			return UNDEFINED;

		if ("zip".equalsIgnoreCase(name))
			return ZIP;

		return UNKOWN;
	}

}
