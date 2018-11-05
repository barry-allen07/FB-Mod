package net.filebot.web;

import static java.util.Comparator.*;

import java.io.Serializable;
import java.net.URL;
import java.util.Comparator;

public class Person implements Serializable {

	protected String name;
	protected String character;
	protected String job;
	protected String department;
	protected Integer order;
	protected URL image;

	public Person() {
		// used by serializer
	}

	public Person(String name, String job) {
		this(name, null, job, null, null, null);
	}

	public Person(String name, String character, String job, String department, Integer order, URL image) {
		this.name = name;
		this.character = character == null || character.isEmpty() ? null : character;
		this.job = job == null || job.isEmpty() ? null : job;
		this.department = department == null || department.isEmpty() ? null : department;
		this.order = order;
		this.image = image;
	}

	public String getName() {
		return name;
	}

	public String getCharacter() {
		return character;
	}

	public String getJob() {
		return job;
	}

	public String getDepartment() {
		return department;
	}

	public Integer getOrder() {
		return order;
	}

	public URL getImage() {
		return image;
	}

	public boolean isActor() {
		return character != null || ACTOR.equals(job) || GUEST_STAR.equals(job);
	}

	public boolean isDirector() {
		return DIRECTOR.equals(job);
	}

	public boolean isWriter() {
		return WRITER.equals(job);
	}

	@Override
	public String toString() {
		return String.format("%s (%s)", name, character != null ? character : job);
	}

	public static final String WRITER = "Writer";
	public static final String DIRECTOR = "Director";
	public static final String ACTOR = "Actor";
	public static final String GUEST_STAR = "Guest Star";

	public static final Comparator<Person> CREDIT_ORDER = comparing(Person::getOrder, nullsLast(naturalOrder()));

}
