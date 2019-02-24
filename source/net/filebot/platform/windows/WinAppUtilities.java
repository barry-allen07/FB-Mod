package net.filebot.platform.windows;

import static javax.swing.BorderFactory.*;
import static net.filebot.Logging.*;

import java.awt.Color;
import java.util.logging.Level;

import javax.swing.UIManager;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WTypes.LPWSTR;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinDef.UINTByReference;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.PointerByReference;

public class WinAppUtilities {

	public static void setAppUserModelID(String aumid) {
		try {
			Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString(aumid));
		} catch (Throwable t) {
			debug.log(Level.WARNING, t.getMessage(), t);
		}
	}

	public static String getAppUserModelID() {
		try {
			PointerByReference ppszAppID = new PointerByReference();
			if (Shell32.INSTANCE.GetCurrentProcessExplicitAppUserModelID(ppszAppID).equals(WinError.S_OK)) {
				return ppszAppID.getValue().getWideString(0);
			}
		} catch (Throwable t) {
			debug.log(Level.WARNING, t.getMessage(), t);
		}
		return null;
	}

	public static String getPackageName() {
		UINTByReference packageFullNameLength = new UINTByReference(new UINT(64));
		LPWSTR packageFullName = new LPWSTR(new Memory(packageFullNameLength.getValue().intValue() * Native.WCHAR_SIZE));

		NativeLong r = Kernel32.INSTANCE.GetCurrentPackageFullName(packageFullNameLength, packageFullName);

		if (r.intValue() != W32Errors.ERROR_SUCCESS) {
			throw new IllegalStateException(String.format("Kernel32.GetCurrentPackageFullName (%s)", r));
		}

		return packageFullName.getValue();
	}

	public static String getPackageAppUserModelID() {
		UINTByReference applicationUserModelIdLength = new UINTByReference(new UINT(64));
		LPWSTR applicationUserModelId = new LPWSTR(new Memory(applicationUserModelIdLength.getValue().intValue() * Native.WCHAR_SIZE));

		NativeLong r = Kernel32.INSTANCE.GetCurrentApplicationUserModelId(applicationUserModelIdLength, applicationUserModelId);

		if (r.intValue() != W32Errors.ERROR_SUCCESS) {
			throw new IllegalStateException(String.format("Kernel32.GetCurrentApplicationUserModelId (%s)", r));
		}

		return applicationUserModelId.getValue();
	}

	public static void initializeApplication(String aumid) {
		if (aumid != null) {
			setAppUserModelID(aumid);
		}

		// improved UI defaults
		UIManager.put("TitledBorder.border", createCompoundBorder(createLineBorder(new Color(0xD7D7D7), 1, true), createCompoundBorder(createMatteBorder(6, 5, 6, 5, new Color(0xE5E5E5)), createEmptyBorder(0, 2, 0, 2))));
	}

	private WinAppUtilities() {
		throw new UnsupportedOperationException();
	}

}
