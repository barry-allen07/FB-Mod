package net.filebot.ui.rename;

import static java.util.stream.Collectors.*;

import java.awt.Component;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.swing.Icon;

import net.filebot.media.LocalDatasource;
import net.filebot.similarity.Match;
import net.filebot.web.Datasource;
import net.filebot.web.SortOrder;

public class LocalFileMatcher implements Datasource, AutoCompleteMatcher {

	private final LocalDatasource datasource;

	public LocalFileMatcher(LocalDatasource datasource) {
		this.datasource = datasource;
	}

	@Override
	public String getIdentifier() {
		return datasource.getIdentifier();
	}

	@Override
	public String getName() {
		return datasource.getName();
	}

	@Override
	public Icon getIcon() {
		return datasource.getIcon();
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		// always use strict mode internally to make behavior more simple and consistent for GUI users
		return datasource.match(files, true).entrySet().stream().map(it -> {
			return new Match<File, Object>(it.getKey(), it.getValue());
		}).collect(toList());
	}

}
