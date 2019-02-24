package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;

public class FileSet extends AbstractSet<Path> {

	private static final int ROOT_LEVEL = -1;

	private final Map<Path, FileSet> folders = new HashMap<Path, FileSet>(4, 2);
	private final Set<Path> files = new HashSet<Path>(4, 2);

	private boolean add(Path e, int depth) {
		// add new leaf element
		if (e.getNameCount() - 1 == depth) {
			return files.add(e.getFileName());
		}

		// add new node element
		return folders.computeIfAbsent(depth == ROOT_LEVEL ? e.getRoot() : e.getName(depth), k -> new FileSet()).add(e, depth + 1);
	}

	@Override
	public boolean add(Path e) {
		return add(e, ROOT_LEVEL);
	}

	public boolean add(File e) {
		return add(e.toPath());
	}

	public boolean add(String e) {
		return add(Paths.get(e));
	}

	private boolean contains(Path e, int depth) {
		// leaf element
		if (e.getNameCount() - 1 == depth) {
			return files.contains(e.getFileName());
		}

		// node element
		if (e.getNameCount() - 1 > depth) {
			FileSet subSet = folders.get(depth == ROOT_LEVEL ? e.getRoot() : e.getName(depth));
			if (subSet != null) {
				return subSet.contains(e, depth + 1);
			}
		}

		return false;
	}

	public boolean contains(Path e) {
		return contains(e, ROOT_LEVEL);
	};

	@Override
	public boolean contains(Object e) {
		return contains(getPath(e));
	};

	protected Path getPath(Object path) {
		if (path instanceof Path) {
			return (Path) path;
		}
		if (path instanceof File) {
			return ((File) path).toPath();
		}
		if (path instanceof String) {
			return Paths.get((String) path);
		}
		if (path instanceof URI) {
			return Paths.get((URI) path);
		}
		return Paths.get(path.toString());
	}

	public Map<Path, List<Path>> getRoots() {
		if (folders.size() != 1 || files.size() > 0) {
			return emptyMap();
		}

		Entry<Path, FileSet> entry = folders.entrySet().iterator().next();
		Path parent = entry.getKey();
		Map<Path, List<Path>> next = entry.getValue().getRoots();
		if (next.size() > 0) {
			// resolve children
			return next.entrySet().stream().collect(toMap(it -> {
				return parent.resolve(it.getKey());
			}, it -> it.getValue()));
		}

		// resolve children
		return folders.entrySet().stream().collect(toMap(it -> it.getKey(), it -> it.getValue().stream().collect(toList())));
	}

	@Override
	public int size() {
		return folders.values().stream().mapToInt(f -> f.size()).sum() + files.size();
	}

	@Override
	public Stream<Path> stream() {
		Stream<Path> descendants = folders.entrySet().stream().flatMap(node -> {
			return node.getValue().stream().map(f -> {
				return node.getKey().resolve(f);
			});
		});

		Stream<Path> children = files.stream();

		return Stream.concat(descendants, children);
	}

	@Override
	public Spliterator<Path> spliterator() {
		return stream().spliterator();
	}

	@Override
	public Iterator<Path> iterator() {
		return stream().iterator();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		folders.values().forEach(FileSet::clear);
		folders.clear();
		files.clear();
	}

	public void load(File f) throws IOException {
		for (String path : readLines(f)) {
			try {
				add(path);
			} catch (InvalidPathException e) {
				debug.warning(e::toString);
			}
		}
	}

	public void append(File f, Collection<?>... paths) throws IOException {
		Files.write(f.toPath(), Stream.of(paths).flatMap(Collection::stream).map(this::getPath).filter(it -> !contains(it)).map(Path::toString).collect(toList()), UTF_8, StandardOpenOption.APPEND);
	}

}
