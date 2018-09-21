package net.filebot.cli;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

import java.util.List;

public enum ConflictAction {

	SKIP, OVERRIDE, FAIL, AUTO, INDEX;

	public static List<String> names() {
		return stream(values()).map(Enum::name).collect(toList());
	}

	public static ConflictAction forName(String name) {
		for (ConflictAction action : values()) {
			if (action.name().equalsIgnoreCase(name)) {
				return action;
			}
		}

		throw new IllegalArgumentException(String.format("%s not in %s", name, names()));
	}

}
