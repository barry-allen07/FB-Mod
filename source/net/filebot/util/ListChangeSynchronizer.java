
package net.filebot.util;


import java.util.List;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.event.ListEvent;
import ca.odell.glazedlists.event.ListEventListener;


public class ListChangeSynchronizer<E> implements ListEventListener<E> {

	private final List<E> target;


	public ListChangeSynchronizer(EventList<E> source, List<E> target) {
		this.target = target;
		source.addListEventListener(this);
	}


	@Override
	public void listChanged(ListEvent<E> listChanges) {
		EventList<E> source = listChanges.getSourceList();

		// update target list
		while (listChanges.next()) {
			int index = listChanges.getIndex();
			int type = listChanges.getType();

			switch (type) {
			case ListEvent.INSERT:
				target.add(index, source.get(index));
				break;
			case ListEvent.UPDATE:
				target.set(index, source.get(index));
				break;
			case ListEvent.DELETE:
				target.remove(index);
				break;
			}
		}
	}


	public static <E> ListChangeSynchronizer<E> syncEventListToList(EventList<E> source, List<E> target) {
		return new ListChangeSynchronizer<E>(source, target);
	}

}
