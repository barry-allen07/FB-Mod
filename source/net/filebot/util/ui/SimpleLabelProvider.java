
package net.filebot.util.ui;

import java.lang.reflect.Method;
import java.util.Arrays;

import javax.swing.Icon;

/**
 * <code>LabelProvider</code> based on reflection.
 */
public class SimpleLabelProvider<T> implements LabelProvider<T> {

	private final Method getIconMethod;
	private final Method getTextMethod;

	/**
	 * Factory method for {@link #SimpleLabelProvider(Class)}.
	 *
	 * @return new <code>LabelProvider</code>
	 */
	public static <T> SimpleLabelProvider<T> forClass(Class<T> type) {
		return new SimpleLabelProvider<T>(type);
	}

	/**
	 * Create a new LabelProvider which will use the <code>getText</code>, <code>getName</code> or <code>toString</code> method for text and the <code>getIcon</code> method for the icon.
	 *
	 * @param type
	 *            a class that has one of the text methods and the icon method
	 */
	public SimpleLabelProvider(Class<T> type) {
		getTextMethod = findAnyMethod(type, "getText", "getName", "toString");
		getIconMethod = findAnyMethod(type, "getIcon");
	}

	/**
	 * Create a new LabelProvider which will use a specified method of a given class
	 *
	 * @param type
	 *            a class with the specified method
	 * @param getText
	 *            a method name such as <code>getText</code>
	 * @param getIcon
	 *            a method name such as <code>getIcon</code>
	 */
	public SimpleLabelProvider(Class<T> type, String getText, String getIcon) {
		getTextMethod = findAnyMethod(type, getText);
		getIconMethod = findAnyMethod(type, getIcon);
	}

	private Method findAnyMethod(Class<T> type, String... names) {
		for (String name : names) {
			try {
				return type.getMethod(name);
			} catch (NoSuchMethodException e) {
				// try next method name
			}
		}

		throw new IllegalArgumentException("Method not found: " + Arrays.toString(names));
	}

	@Override
	public String getText(T value) {
		try {
			return (String) getTextMethod.invoke(value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Icon getIcon(T value) {
		try {
			return (Icon) getIconMethod.invoke(value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
