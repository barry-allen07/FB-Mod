package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.util.List;

public enum SortOrder {

	Airdate, DVD, Absolute, AbsoluteAirdate;

	@Override
	public String toString() {
		switch (this) {
		case Airdate:
			return "Airdate Order";
		case DVD:
			return "DVD Order";
		case Absolute:
			return "Absolute Order";
		default:
			return "Absolute Airdate Order";
		}
	}

	public static List<String> names() {
		return stream(values()).map(Enum::name).collect(toList());
	}

	public static SortOrder forName(String name) {
		for (SortOrder order : SortOrder.values()) {
			if (order.name().equalsIgnoreCase(name)) {
				return order;
			}
		}

		throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
	}

}
