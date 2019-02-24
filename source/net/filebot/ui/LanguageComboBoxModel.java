package net.filebot.ui;

import static net.filebot.Language.*;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;

import net.filebot.Language;

public class LanguageComboBoxModel extends AbstractListModel implements ComboBoxModel {

	public static final Language ALL_LANGUAGES = new Language("undefined", "und", "und", "und", new String[] { "All Languages" });

	private Language defaultLanguage;
	private Language selection;

	private List<Language> favorites = new Favorites(2);

	private List<Language> values = availableLanguages();

	public LanguageComboBoxModel(Language defaultLanguage, Language initialSelection) {
		this.defaultLanguage = defaultLanguage;
		this.selection = initialSelection;
	}

	@Override
	public Language getElementAt(int index) {
		// "All Languages"
		if (index == 0)
			return defaultLanguage;

		// "All Languages" offset
		index -= 1;

		if (index < favorites.size()) {
			return favorites.get(index);
		}

		// "Favorites" offset
		index -= favorites.size();

		return values.get(index);
	}

	@Override
	public int getSize() {
		// "All Languages" : favorites[] : values[]
		return 1 + favorites.size() + values.size();
	}

	public List<Language> favorites() {
		return favorites;
	}

	@Override
	public Language getSelectedItem() {
		return selection;
	}

	@Override
	public void setSelectedItem(Object value) {
		if (value instanceof Language) {
			Language language = (Language) value;
			selection = ALL_LANGUAGES.getCode().equals(language.getCode()) ? ALL_LANGUAGES : language;
		}
	}

	protected int convertFavoriteIndexToModel(int favoriteIndex) {
		return 1 + favoriteIndex;
	}

	protected void fireFavoritesAdded(int from, int to) {
		fireIntervalAdded(this, convertFavoriteIndexToModel(from), convertFavoriteIndexToModel(to));
	}

	protected void fireFavoritesRemoved(int from, int to) {
		fireIntervalRemoved(this, convertFavoriteIndexToModel(from), convertFavoriteIndexToModel(to));
	}

	private class Favorites extends AbstractList<Language> {

		private final List<Language> data;

		private final int capacity;

		public Favorites(int capacity) {
			this.data = new ArrayList<Language>(capacity);
			this.capacity = capacity;
		}

		@Override
		public Language get(int index) {
			return data.get(index);
		}

		@Override
		public boolean add(Language element) {
			// add first
			return addIfAbsent(0, element);
		}

		@Override
		public void add(int index, Language element) {
			addIfAbsent(index, element);
		}

		public boolean addIfAbsent(int index, Language element) {
			// 1. ignore null values
			// 2. ignore ALL_LANGUAGES
			// 3. make sure there are no duplicates
			// 4. limit size to capacity
			if (element == null || element == ALL_LANGUAGES || element.getCode().equals(defaultLanguage.getCode()) || contains(element) || index >= capacity) {
				return false;
			}

			// make sure there is free space
			if (data.size() >= capacity) {
				// remove last
				remove(data.size() - 1);
			}

			// add clone of language, because KeySelection behaviour will
			// get weird if the same object is in the model multiple times
			data.add(index, element.clone());

			// update view
			fireFavoritesAdded(index, index);

			return true;
		}

		@Override
		public boolean contains(Object obj) {
			// check via language code, because data consists of clones
			if (obj instanceof Language) {
				Language language = (Language) obj;

				for (Language element : data) {
					if (language.getCode().equals(element.getCode()))
						return true;
				}
			}

			return false;
		}

		@Override
		public Language remove(int index) {
			Language old = data.remove(index);

			// update view
			fireFavoritesRemoved(index, index);

			return old;
		}

		@Override
		public int size() {
			return data.size();
		}
	}

}
