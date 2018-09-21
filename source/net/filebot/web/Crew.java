package net.filebot.web;

import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.function.Predicate;

public interface Crew {

	List<Person> getCrew();

	default List<Person> getCast() {
		return getCrew().stream().filter(Person::isActor).collect(toList());
	}

	default List<String> getActors() {
		return getCrewNames(Person::isActor);
	}

	default List<String> getDirectors() {
		return getCrewNames(Person::isDirector);
	}

	default List<String> getWriters() {
		return getCrewNames(Person::isWriter);
	}

	default String getDirector() {
		return getCrewName(Person::isDirector);
	}

	default String getWriter() {
		return getCrewName(Person::isWriter);
	}

	default String getCrewName(Predicate<Person> filter) {
		return getCrew().stream().filter(filter).map(Person::getName).findFirst().orElse(null);
	}

	default List<String> getCrewNames(Predicate<Person> filter) {
		return getCrew().stream().filter(filter).map(Person::getName).collect(toList());
	}

}
