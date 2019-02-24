package net.filebot.ui;

import static java.awt.event.ItemEvent.*;
import static net.filebot.Language.*;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.AbstractList;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import javax.swing.JComboBox;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import net.filebot.Language;
import net.filebot.Settings;

public class LanguageComboBox extends JComboBox {

	private Entry<String, String> persistentSelectedLanguage;
	private List<String> persistentFavoriteLanguages;

	public LanguageComboBox(Language initialSelection, Settings settings) {
		super(new LanguageComboBoxModel(initialSelection, initialSelection));
		setRenderer(new LanguageComboBoxCellRenderer(super.getRenderer()));

		if (settings != null) {
			persistentSelectedLanguage = settings.entry("language.selected");
			persistentFavoriteLanguages = settings.node("language.favorites").asList();
		} else {
			persistentSelectedLanguage = new SimpleEntry<String, String>(null, null);
			persistentFavoriteLanguages = new ArrayList<String>();
		}

		// restore selected language
		try {
			getModel().setSelectedItem(Language.getLanguage(persistentSelectedLanguage.getValue()));
		} catch (Exception e) {
			getModel().setSelectedItem(LanguageComboBoxModel.ALL_LANGUAGES);
		}

		// restore favorite languages
		for (String favoriteLanguage : persistentFavoriteLanguages) {
			Language language = getLanguage(favoriteLanguage);
			if (language != null) {
				getModel().favorites().add(getModel().favorites().size(), language);
			}
		}

		// guess favorite languages
		if (getModel().favorites().isEmpty()) {
			for (Locale locale : new Locale[] { Locale.ENGLISH, Locale.getDefault() }) {
				getModel().favorites().add(getLanguage(locale.getLanguage()));
			}
		}

		// update favorites on change
		addPopupMenuListener(new PopupSelectionListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				Language language = (Language) e.getItem();

				if (getModel().favorites().add(language)) {
					persistentFavoriteLanguages.clear();
					persistentFavoriteLanguages.addAll(new AbstractList<String>() {

						@Override
						public String get(int index) {
							return getModel().favorites().get(index).getCode();
						}

						@Override
						public int size() {
							return getModel().favorites().size();
						}
					});
				}

				persistentSelectedLanguage.setValue(language.getCode());
			}
		});
	}

	@Override
	public LanguageComboBoxModel getModel() {
		return (LanguageComboBoxModel) super.getModel();
	}

	private static class PopupSelectionListener implements PopupMenuListener, ItemListener {

		private Object selected = null;

		@Override
		public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
			JComboBox comboBox = (JComboBox) e.getSource();

			// selected item before popup
			selected = comboBox.getSelectedItem();
		}

		@Override
		public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			JComboBox comboBox = (JComboBox) e.getSource();

			// check selected item after popup
			if (selected != comboBox.getSelectedItem()) {
				itemStateChanged(new ItemEvent(comboBox, ITEM_STATE_CHANGED, comboBox.getSelectedItem(), SELECTED));
			}

			selected = null;
		}

		@Override
		public void popupMenuCanceled(PopupMenuEvent e) {
			selected = null;
		}

		@Override
		public void itemStateChanged(ItemEvent e) {

		}
	}

}
