
package net.filebot.ui.rename;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.TransformedList;
import ca.odell.glazedlists.event.ListEvent;
import net.filebot.similarity.Match;


public class MatchModel<Value, Candidate> {

	private final EventList<Match<Value, Candidate>> source = new BasicEventList<Match<Value, Candidate>>();

	private final EventList<Value> values;

	private final EventList<Candidate> candidates;


	public MatchModel() {
		this.values = new MatchView<Value, Candidate>(source) {

			@Override
			public Value getElement(Match<Value, Candidate> match) {
				return match.getValue();
			}


			@Override
			public Candidate getComplement(Match<Value, Candidate> match) {
				return match.getCandidate();
			}


			@Override
			public Match<Value, Candidate> createMatch(Value element, Candidate complement) {
				return new Match<Value, Candidate>(element, complement);
			}
		};

		this.candidates = new MatchView<Candidate, Value>(source) {

			@Override
			public Candidate getElement(Match<Value, Candidate> match) {
				return match.getCandidate();
			}


			@Override
			public Value getComplement(Match<Value, Candidate> match) {
				return match.getValue();
			}


			@Override
			public Match<Value, Candidate> createMatch(Candidate element, Value complement) {
				return new Match<Value, Candidate>(complement, element);
			}
		};
	}


	public void clear() {
		source.clear();
	}


	public int size() {
		return source.size();
	}


	public Match<Value, Candidate> getMatch(int index) {
		return source.get(index);
	}


	public boolean hasComplement(int index) {
		if (index >= 0 && index < size()) {
			return source.get(index).getValue() != null && source.get(index).getCandidate() != null;
		}

		return false;
	}


	public EventList<Match<Value, Candidate>> matches() {
		return source;
	}


	public EventList<Value> values() {
		return values;
	}


	public EventList<Candidate> candidates() {
		return candidates;
	}


	public void addAll(Collection<Match<Value, Candidate>> matches) {
		source.addAll(matches);
	}


	public void addAll(Collection<Value> values, Collection<Candidate> candidates) {
		if (this.values.size() != this.candidates.size())
			throw new IllegalStateException("Existing matches are not balanced");

		Iterator<Value> valueIterator = values.iterator();
		Iterator<Candidate> candidateIterator = candidates.iterator();

		while (valueIterator.hasNext() || candidateIterator.hasNext()) {
			Value value = valueIterator.hasNext() ? valueIterator.next() : null;
			Candidate candidate = candidateIterator.hasNext() ? candidateIterator.next() : null;

			source.add(new Match<Value, Candidate>(value, candidate));
		}
	}


	private abstract class MatchView<Element, Complement> extends TransformedList<Match<Value, Candidate>, Element> {

		public MatchView(EventList<Match<Value, Candidate>> source) {
			super(source);

			source.addListEventListener(this);
		}


		public abstract Element getElement(Match<Value, Candidate> match);


		public abstract Complement getComplement(Match<Value, Candidate> match);


		public abstract Match<Value, Candidate> createMatch(Element element, Complement complement);


		@Override
		public Element get(int index) {
			return getElement(index);
		}


		public Element getElement(int index) {
			return getElement(source.get(index));
		}


		public Complement getComplement(int index) {
			return getComplement(source.get(index));
		}


		@Override
		public boolean addAll(Collection<? extends Element> values) {
			return put(size(), values);
		}


		@Override
		public boolean add(Element value) {
			return put(size(), Collections.singleton(value));
		};


		@Override
		public void add(int index, Element value) {
			List<Element> range = new ArrayList<Element>();

			range.add(value);
			range.addAll(subList(index, size()));

			put(index, range);
		}


		@Override
		public Element remove(int index) {
			Element old = getElement(index);

			int lastIndex = size() - 1;

			// shift subsequent elements
			put(index, new ArrayList<Element>(subList(index + 1, lastIndex + 1)));

			// remove last element
			if (getComplement(lastIndex) == null) {
				source.remove(lastIndex);
			} else {
				set(lastIndex, null);
			}

			return old;
		}


		@Override
		public Element set(int index, Element element) {
			Element old = getElement(index);

			source.set(index, createMatch(element, getComplement(index)));

			return old;
		}


		@Override
		public void clear() {
			// remove in reverse, because null matches may only
			// exist at the and of the source model
			for (int i = size() - 1; i >= 0; i--) {
				Complement complement = getComplement(i);

				if (complement != null) {
					// replace original match with null match
					source.set(i, createMatch(null, complement));
				} else {
					// remove match if value and candidate are null
					source.remove(i);
				}
			}
		}


		private boolean put(int index, Collection<? extends Element> elements) {
			for (Element element : elements) {
				if (index < source.size()) {
					set(index, element);
				} else {
					source.add(index, createMatch(element, null));
				}

				index++;
			}

			return true;
		}


		@Override
		protected boolean isWritable() {
			// can't write to source directly
			return false;
		}

		private int size = 0;


		@Override
		public int size() {
			return size;
		}


		@Override
		public void listChanged(ListEvent<Match<Value, Candidate>> listChanges) {
			updates.beginEvent(true);

			while (listChanges.next()) {
				int index = listChanges.getIndex();
				int type = listChanges.getType();

				if (type == ListEvent.INSERT || type == ListEvent.UPDATE) {
					if (index < size) {
						if (index == size - 1 && getElement(index) == null) {
							updates.elementDeleted(index, null);
							size--;
						} else {
							updates.elementUpdated(index, null, getElement(index));
						}
					} else if (index == size && getElement(index) != null) {
						updates.elementInserted(index, getElement(index));
						size++;
					}
				} else if (type == ListEvent.DELETE && index < size) {
					updates.elementDeleted(index, null);
					size--;
				}
			}

			updates.commitEvent();
		}
	}

}
