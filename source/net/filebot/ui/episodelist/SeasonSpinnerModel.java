package net.filebot.ui.episodelist;

import static java.util.Collections.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.SpinnerListModel;

class SeasonSpinnerModel extends SpinnerListModel {

	public static final int ALL_SEASONS = 0;

	public static final int YEAR_SEASON_MIN_VALUE = 1990;
	public static final int YEAR_SEASON_MAX_VALUE = 2100;

	public static final int SEASON_MIN_VALUE = 1;
	public static final int SEASON_MAX_VALUE = 50;

	public static List<Integer> getSeasonValues() {
		IntStream values = IntStream.of(ALL_SEASONS);
		values = IntStream.concat(values, IntStream.range(SEASON_MIN_VALUE, SEASON_MAX_VALUE));
		values = IntStream.concat(values, IntStream.range(YEAR_SEASON_MIN_VALUE, YEAR_SEASON_MAX_VALUE));
		return values.boxed().collect(Collectors.toList());
	}

	public SeasonSpinnerModel() {
		super(getSeasonValues());
	}

	public int getSeason() {
		return ((Integer) getValue()).intValue();
	}

	public void spin(int steps) {
		for (int i = 0; i < Math.abs(steps); i++) {
			setValue(i < 0 ? getPreviousValue() : getNextValue());
		}
	}

	private Object valueBeforeLock = null;

	public void lock(int value) {
		valueBeforeLock = getValue();

		setList(singletonList(ALL_SEASONS));
		setValue(ALL_SEASONS);
	}

	public void unlock() {
		setList(getSeasonValues());

		if (valueBeforeLock != null) {
			setValue(valueBeforeLock);
		}
		valueBeforeLock = null;
	}

}
