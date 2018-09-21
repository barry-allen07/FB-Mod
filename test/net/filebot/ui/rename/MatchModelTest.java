
package net.filebot.ui.rename;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ca.odell.glazedlists.GlazedLists;
import net.filebot.similarity.Match;


public class MatchModelTest {

	@Test
	public void addAll() {
		MatchModel<String, Integer> model = new MatchModel<String, Integer>();

		List<String> names = Arrays.asList("A", "B", "C", "D", "E");
		List<Integer> values = Arrays.asList(1, 2, 3);

		model.addAll(Arrays.asList("A", "B", "C", "D", "E"), Arrays.asList(1, 2, 3));

		assertEquals(5, model.size(), 0);

		for (int i = 0; i < model.size(); i++) {
			String name = i < names.size() ? names.get(i) : null;
			Integer value = i < values.size() ? values.get(i) : null;

			// check model and views
			assertMatchEquals(name, value, model.matches().get(i));
			assertEquals(name, model.values().get(i));
			assertEquals(value, model.candidates().get(i));
		}
	}


	@Test
	public void matchViewElements() {
		MatchModel<String, Integer> model = new MatchModel<String, Integer>();
		model.addAll(Arrays.asList("A", "B", "C"), Arrays.asList(1, 2, 3, 4, 5));

		model.values().add("D");
		assertMatchEquals("D", 4, model.getMatch(3));

		model.values().add(1, "A2");
		assertMatchEquals("C", 4, model.getMatch(3));

		model.candidates().remove(3);
		assertMatchEquals("C", 5, model.getMatch(3));

		model.candidates().remove(3);
		assertMatchEquals("C", null, model.getMatch(3));

		model.matches().remove(0);
		assertMatchEquals("A2", 2, model.getMatch(0));

		model.values().set(0, "A");
		assertMatchEquals("A", 2, model.getMatch(0));
	}


	@Test
	public void matchViewClear() {
		MatchModel<String, Integer> model = new MatchModel<String, Integer>();

		model.values().addAll(Arrays.asList("A", "B", "C"));
		model.candidates().addAll(Arrays.asList(1, 2, 3, 4, 5));

		model.values().clear();

		assertEquals(0, model.values().size(), 0);
		assertEquals(5, model.candidates().size(), 0);

		model.values().addAll(Arrays.asList("A", "B", "C"));

		assertMatchEquals("A", 1, model.getMatch(0));
		assertMatchEquals("C", 3, model.getMatch(2));
	}


	@Test
	public void matchViewListEvents() {
		MatchModel<String, Integer> model = new MatchModel<String, Integer>();

		ArrayList<String> copy = new ArrayList<String>();
		GlazedLists.syncEventListToList(model.values(), copy);

		model.addAll(Arrays.asList("A", "B", "C"), Arrays.asList(1, 2, 3, 4, 5));

		assertArrayEquals(Arrays.asList("A", "B", "C").toArray(), copy.toArray());
	}


	private static <V, C> void assertMatchEquals(V expectedValue, C expectedCandidate, Match<V, C> actual) {
		assertEquals(expectedValue, actual.getValue());
		assertEquals(expectedCandidate, actual.getCandidate());
	}
}
