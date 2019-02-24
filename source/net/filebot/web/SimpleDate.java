package net.filebot.web;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDate implements Serializable, Comparable<Object> {

	protected int year;
	protected int month;
	protected int day;

	public SimpleDate() {
		// used by deserializer
	}

	public SimpleDate(int year, int month, int day) {
		this.year = year;
		this.month = month;
		this.day = day;
	}

	public SimpleDate(Temporal date) {
		this(date.get(ChronoField.YEAR), date.get(ChronoField.MONTH_OF_YEAR), date.get(ChronoField.DAY_OF_MONTH));
	}

	public SimpleDate(long t) {
		this(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()));
	}

	public int getYear() {
		return year;
	}

	public int getMonth() {
		return month;
	}

	public int getDay() {
		return day;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SimpleDate) {
			SimpleDate other = (SimpleDate) obj;
			return year == other.year && month == other.month && day == other.day;
		} else if (obj instanceof CharSequence) {
			return toString().equals(obj.toString());
		}

		return super.equals(obj);
	}

	@Override
	public int compareTo(Object other) {
		if (other instanceof SimpleDate) {
			return compareTo((SimpleDate) other);
		} else if (other instanceof CharSequence) {
			SimpleDate otherDate = parse(other.toString());
			if (otherDate != null) {
				return compareTo(otherDate);
			}
		}

		throw new IllegalArgumentException(String.valueOf(other));
	}

	public int compareTo(SimpleDate other) {
		return Long.compare(getTimeStamp(), other.getTimeStamp());
	}

	@Override
	public int hashCode() {
		return Objects.hash(year, month, day);
	}

	@Override
	public SimpleDate clone() {
		return new SimpleDate(year, month, day);
	}

	public String format(String pattern) {
		return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(toLocalDate());
	}

	public LocalDate toLocalDate() {
		return LocalDate.of(year, month, day);
	}

	public Instant toInstant() {
		return toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
	}

	public long getTimeStamp() {
		return toInstant().toEpochMilli();
	}

	@Override
	public String toString() {
		return String.format("%04d-%02d-%02d", year, month, day);
	}

	public static SimpleDate parse(String date) {
		if (date != null && date.length() > 0) {
			Matcher m = DATE_FORMAT.matcher(date);
			if (m.matches()) {
				return new SimpleDate(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
			}
		}
		return null;
	}

	public static final Pattern DATE_FORMAT = Pattern.compile("(\\d{4})\\D(\\d{1,2})\\D(\\d{1,2})");

}
