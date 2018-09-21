
package net.filebot.util;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;


public class FilterIteratorTest {

	private List<String> list = new ArrayList<String>();


	@Before
	public void setUp() {
		list.add("first one");
		list.add("2");
		list.add("third space");
		list.add("four square");
		list.add("5");
		list.add("last order");
	}


	@Test
	public void iterateAndRemove() {
		Iterator<Integer> integers = new FilterIterator<String, Integer>(list) {

			@Override
			protected Integer filter(String sourceValue) {
				if (sourceValue.matches("\\d+"))
					return Integer.valueOf(sourceValue);

				return null;
			}
		};

		assertEquals(Integer.valueOf(2), integers.next());
		integers.remove();
		assertEquals(Integer.valueOf(5), integers.next());
		integers.remove();

		assertFalse(integers.hasNext());

		// check if remove() worked
		Iterator<String> strings = list.iterator();
		assertEquals("first one", strings.next());
		assertEquals("third space", strings.next());
		assertEquals("four square", strings.next());
		assertEquals("last order", strings.next());

		assertFalse(strings.hasNext());
	}
}
