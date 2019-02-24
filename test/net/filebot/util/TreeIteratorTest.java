
package net.filebot.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TreeIteratorTest {

	private List<Object> tree;

	@Before
	public void setUp() throws Exception {
		tree = new ArrayList<Object>();

		tree.add("r1");

		List<Object> branch = new ArrayList<Object>();
		branch.add("b1");
		branch.add("b2");

		tree.add(branch);

		tree.add("r2");

		List<Object> treetop = new ArrayList<Object>();
		treetop.add("t1");
		treetop.add("t2");
		treetop.add("t3");

		List<Object> trunk = new ArrayList<Object>();
		trunk.add(treetop);

		tree.add(trunk);
	}

	@Test
	public void iterate() {
		TreeIterator<Object> treeIterator = new TreeIterator<Object>(tree) {

			@Override
			protected Iterator<Object> children(Object node) {
				if (node instanceof Iterable)
					return ((Iterable) node).iterator();

				return null;
			}
		};

		// check leafs (String) and nodes (Iterable)
		assertTrue(treeIterator.next() instanceof Iterable); // root
		assertEquals("r1", treeIterator.next());
		assertTrue(treeIterator.next() instanceof Iterable); // branch
		assertEquals("b1", treeIterator.next());
		assertEquals("b2", treeIterator.next());
		assertEquals("r2", treeIterator.next());
		assertTrue(treeIterator.next() instanceof Iterable); // trunk
		assertTrue(treeIterator.next() instanceof Iterable); // treetop
		assertEquals("t1", treeIterator.next());
		assertEquals("t2", treeIterator.next());
		assertEquals("t3", treeIterator.next());

		assertFalse(treeIterator.hasNext());
	}
}
