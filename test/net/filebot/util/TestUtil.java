
package net.filebot.util;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class TestUtil {

	public static List<Object[]> asParameters(Object... parameters) {
		List<Object[]> list = new ArrayList<Object[]>();

		for (Object parameter : parameters) {
			list.add(new Object[] { parameter });
		}

		return list;
	}


	public static List<Object[]> asParameters(Collection<?> parameters) {
		return asParameters(parameters.toArray());
	}


	public static <T> List<List<T>> rotations(Collection<T> source) {
		List<List<T>> rotations = new ArrayList<List<T>>();

		for (int i = 0; i < source.size(); i++) {
			List<T> copy = new ArrayList<T>(source);
			Collections.rotate(copy, i);
			rotations.add(copy);
		}

		return rotations;
	}

}
