package net.filebot;

import static java.util.Collections.*;
import static net.filebot.Logging.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "history")
public class History {

	@XmlElement(name = "sequence")
	private List<Sequence> sequences;

	public History() {
		this.sequences = new ArrayList<Sequence>();
	}

	public History(Collection<Sequence> sequences) {
		this.sequences = new ArrayList<Sequence>(sequences);
	}

	public static class Sequence {

		@XmlAttribute(name = "date", required = true)
		private Date date;

		@XmlElement(name = "rename", required = true)
		private List<Element> elements;

		private Sequence() {
			// hide constructor
		}

		public Date date() {
			return date;
		}

		public List<Element> elements() {
			if (elements == null)
				return emptyList();

			return unmodifiableList(elements);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Sequence) {
				Sequence other = (Sequence) obj;
				return date.equals(other.date) && elements.equals(other.elements);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(elements, date);
		}
	}

	public static class Element {

		@XmlAttribute(name = "dir", required = true)
		private File dir;

		@XmlAttribute(name = "from", required = true)
		private String from;

		@XmlAttribute(name = "to", required = true)
		private String to;

		public Element() {
			// used by JAXB
		}

		public Element(String from, String to, File dir) {
			this.from = from;
			this.to = to;
			this.dir = dir;
		}

		public File dir() {
			return dir;
		}

		public String from() {
			return from;
		}

		public String to() {
			return to;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Element) {
				Element element = (Element) obj;
				return to.equals(element.to) && from.equals(element.from) && dir.getPath().equals(element.dir.getPath());
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(to, from, dir);
		}
	}

	public List<Sequence> sequences() {
		return unmodifiableList(sequences);
	}

	public void add(Collection<Element> elements) {
		Sequence sequence = new Sequence();
		sequence.date = new Date();
		sequence.elements = new ArrayList<Element>(elements);

		add(sequence);
	}

	public void add(Sequence sequence) {
		this.sequences.add(sequence);
	}

	public void addAll(Collection<Sequence> sequences) {
		this.sequences.addAll(sequences);
	}

	public void merge(History history) {
		for (Sequence sequence : history.sequences()) {
			if (!sequences.contains(sequence)) {
				add(sequence);
			}
		}
	}

	public int totalSize() {
		int i = 0;
		for (Sequence it : sequences()) {
			i += it.elements.size();
		}
		return i;
	}

	public void clear() {
		sequences.clear();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof History) {
			History other = (History) obj;
			return sequences.equals(other.sequences);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return sequences.hashCode();
	}

	public Map<File, File> getRenameMap() {
		Map<File, File> map = new LinkedHashMap<File, File>();
		for (History.Sequence seq : this.sequences()) {
			for (History.Element elem : seq.elements()) {
				File to = new File(elem.to());
				if (!to.isAbsolute()) {
					to = new File(elem.dir(), elem.to());
				}
				File from = new File(elem.dir(), elem.from());
				map.put(from, to);
			}
		}
		return map;
	}

	public static void exportHistory(History history, OutputStream output) {
		try {
			Marshaller marshaller = JAXBContext.newInstance(History.class).createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.marshal(history, output);
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to write history", e);
		}
	}

	public static History importHistory(InputStream stream) {
		try {
			Unmarshaller unmarshaller = JAXBContext.newInstance(History.class).createUnmarshaller();
			return ((History) unmarshaller.unmarshal(stream));
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to read history", e);
		}

		// default to empty history
		return new History();
	}

}
