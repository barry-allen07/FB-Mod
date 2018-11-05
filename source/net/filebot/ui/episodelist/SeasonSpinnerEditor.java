package net.filebot.ui.episodelist;

import static net.filebot.ui.episodelist.SeasonSpinnerModel.*;

import java.awt.Color;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SwingConstants;
import javax.swing.text.DefaultFormatter;
import javax.swing.text.DefaultFormatterFactory;

class SeasonSpinnerEditor extends DefaultEditor {

	public SeasonSpinnerEditor(JSpinner spinner) {
		super(spinner);

		getTextField().setFormatterFactory(new DefaultFormatterFactory(new DefaultFormatter() {

			@Override
			public Object stringToValue(String string) throws ParseException {
				if ("All Seasons".equals(string)) {
					return ALL_SEASONS;
				}

				Matcher matcher = Pattern.compile("Season (\\d+)").matcher(string);

				if (matcher.matches()) {
					return Integer.valueOf(matcher.group(1));
				}

				// negative season number
				throw new ParseException("Illegal season number", 0);
			}

			@Override
			public String valueToString(Object value) throws ParseException {
				int season = ((Number) value).intValue();

				if (season == ALL_SEASONS)
					return "All Seasons";
				else if (season >= 1)
					return String.format("Season %d", season);

				// negative season number
				throw new ParseException("Illegal season number", 0);
			}

		}));

		getTextField().setHorizontalAlignment(SwingConstants.RIGHT);
		getTextField().setBackground(Color.white);
	}
}
