package net.filebot;

import static java.nio.file.Files.*;
import static java.nio.file.Paths.*;
import static net.filebot.platform.windows.WinAppUtilities.*;

public enum LicenseModel {

	MicrosoftStore {

		private final Resource<Boolean> AUMID = Resource.lazy(() -> getPackageName().startsWith("PointPlanck.FileBot"));

		@Override
		public Object check() throws LicenseError {
			try {
				if (!AUMID.get()) {
					throw new LicenseError("Desktop Bridge not found");
				}
			} catch (Exception e) {
				throw new LicenseError(e.getMessage());
			}

			return "Microsoft Store License";
		}
	},

	MacAppStore {

		private final Resource<Boolean> SANDBOX = Resource.lazy(() -> System.getenv("APP_SANDBOX_CONTAINER_ID").equals("net.filebot.FileBot") && !isReadable(get("/tmp")));

		@Override
		public Object check() throws LicenseError {
			try {
				if (!SANDBOX.get()) {
					throw new LicenseError("Mac App Sandbox not found");
				}
			} catch (Exception e) {
				throw new LicenseError(e.getMessage());
			}

			return "Mac App Store License";
		}
	},

	PGPSignedMessage {

		@Override
		public License check() throws LicenseError {
			try {
				return License.INSTANCE.get().check();
			} catch (Exception e) {
				throw new LicenseError(e.getMessage());
			}
		}
	};

	public boolean isFile() {
		return this == PGPSignedMessage;
	}

	public abstract Object check() throws LicenseError;

}
