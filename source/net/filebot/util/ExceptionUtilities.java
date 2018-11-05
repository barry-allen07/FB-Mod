package net.filebot.util;

public final class ExceptionUtilities {

	public static Throwable getRootCause(Throwable t) {
		if (t != null) {
			while (t.getCause() != null) {
				t = t.getCause();
			}
		}
		return t;
	}

	public static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
		while (t != null) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
			t = t.getCause();
		}
		return null;
	}

	public static String getRootCauseMessage(Throwable t) {
		return getMessage(getRootCause(t));
	}

	public static String getMessage(Throwable t) {
		if (t != null) {
			String m = t.getMessage();
			if (m == null || m.isEmpty()) {
				return t.toString();
			}
			return m;
		}
		return null;
	}

	private ExceptionUtilities() {
		throw new UnsupportedOperationException();
	}

}
