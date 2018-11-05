package net.filebot.hash;

import java.util.zip.CRC32;

import net.filebot.MediaTypes;
import net.filebot.util.FileUtilities.ExtensionFileFilter;

public enum HashType {

	SFV {

		@Override
		public Hash newHash() {
			return new ChecksumHash(new CRC32());
		}

		@Override
		public VerificationFormat getFormat() {
			// e.g folder/file.txt 970E4EF1
			return new SfvFormat();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("verification/sfv");
		}
	},

	MD5 {

		@Override
		public Hash newHash() {
			return new MessageDigestHash("MD5");
		}

		@Override
		public VerificationFormat getFormat() {
			// e.g. 50e85fe18e17e3616774637a82968f4c *folder/file.txt
			return new VerificationFormat();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("verification/md5sum");
		}
	},

	SHA1 {

		@Override
		public Hash newHash() {
			return new MessageDigestHash("SHA-1");
		}

		@Override
		public VerificationFormat getFormat() {
			// e.g 1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA1*folder/file.txt
			return new VerificationFormat("SHA1");
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("verification/sha1sum");
		}

		@Override
		public String toString() {
			return "SHA1";
		}
	},

	SHA256 {

		@Override
		public Hash newHash() {
			return new MessageDigestHash("SHA-256");
		}

		@Override
		public VerificationFormat getFormat() {
			// e.g 1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA256*folder/file.txt
			return new VerificationFormat("SHA256");
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("verification/sha256sum");
		}

		@Override
		public String toString() {
			return "SHA2";
		}
	},

	// SHA3_384 {
	//
	// @Override
	// public Hash newHash() {
	// return new MessageDigestHash("SHA3-384");
	// }
	//
	// @Override
	// public VerificationFormat getFormat() {
	// // e.g 1a02a7c1e9ac91346d08829d5037b240f42ded07 ?SHA3-384*folder/file.txt
	// return new VerificationFormat("SHA3-384");
	// }
	//
	// @Override
	// public ExtensionFileFilter getFilter() {
	// return MediaTypes.getTypeFilter("verification/sha3sum");
	// }
	//
	// @Override
	// public String toString() {
	// return "SHA3";
	// }
	// },

	ED2K {

		@Override
		public Hash newHash() {
			return JacksumHash.newED2K();
		}

		@Override
		public VerificationFormat getFormat() {
			return new VerificationFormat();
		}

		@Override
		public ExtensionFileFilter getFilter() {
			return MediaTypes.getTypeFilter("verification/ed2k");
		}

		@Override
		public String toString() {
			return "ED2K";
		}
	};

	public abstract Hash newHash();

	public abstract VerificationFormat getFormat();

	public abstract ExtensionFileFilter getFilter();

}
