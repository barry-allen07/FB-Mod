
package net.filebot.util;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.filebot.util.PreferencesMap.SimpleAdapter;


public class PreferencesListTest {

	private static Preferences root;
	private static Preferences strings;
	private static Preferences numbers;
	private static Preferences temp;


	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		root = Preferences.userRoot().node("junit-test");

		strings = root.node("strings");
		strings.put("0", "Rei");
		strings.put("1", "Firefly");
		strings.put("2", "Roswell");
		strings.put("3", "Angel");
		strings.put("4", "Dead like me");
		strings.put("5", "Babylon");

		numbers = root.node("numbers");
		numbers.putInt("0", 4);
		numbers.putInt("1", 5);
		numbers.putInt("2", 2);

		temp = root.node("temp");
	}


	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		root.removeNode();
	}


	@Test
	public void get() {
		List<String> list = PreferencesList.map(strings);

		assertEquals("Rei", list.get(0));
		assertEquals("Roswell", list.get(2));
		assertEquals("Babylon", list.get(5));
	}


	@Test
	public void add() {
		List<Integer> list = PreferencesList.map(numbers, SimpleAdapter.forClass(Integer.class));

		list.add(3);

		assertEquals("3", numbers.get("3", null));
	}


	@Test
	public void remove() {

		ArrayList<String> compareValues = new ArrayList<String>();

		compareValues.add("Gladiator 1");
		compareValues.add("Gladiator 2");
		compareValues.add("Gladiator 3");
		compareValues.add("Gladiator 4");
		compareValues.add("Gladiator 5");

		List<String> prefs = PreferencesList.map(temp);
		prefs.addAll(compareValues);

		for (int index : new int[] { 4, 0, 1 }) {
			prefs.remove(index);
			compareValues.remove(index);

			assertArrayEquals(compareValues.toArray(), prefs.toArray());
		}

	}


	@Test
	public void setEntry() {
		List<String> list = PreferencesList.map(strings);

		list.set(3, "Buffy");

		assertEquals(strings.get("3", null), "Buffy");
	}


	@Test
	public void toArray() throws Exception {
		List<String> list = PreferencesList.map(strings);

		assertArrayEquals(list.subList(0, 3).toArray(), new Object[] { "Rei", "Firefly", "Roswell" });
	}
}
