package net.filebot.cli;

import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.MapOptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class BindingsHandler extends MapOptionHandler {

	public BindingsHandler(CmdLineParser parser, OptionDef option, Setter<? super Map<?, ?>> setter) {
		super(parser, option, setter);
	}

	@Override
	public String getDefaultMetaVariable() {
		return "name=value";
	}

	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		FieldSetter fs = setter.asFieldSetter();
		Map map = (Map) fs.getValue();
		if (map == null) {
			map = createNewCollection(fs.getType());
			fs.addValue(map);
		}

		int pos = 0;
		while (pos < params.size()) {
			if (params.getParameter(pos).startsWith("-")) {
				return pos;
			}

			String[] nv = EQUALS.split(params.getParameter(pos), 2);
			if (nv.length < 2) {
				return pos;
			}

			String n = getName(nv[0]);
			String v = getValue(nv[1]);

			addToMap(map, n, v);
			pos++;
		}

		return pos;
	}

	public String getName(String n) throws CmdLineException {
		if (!isIdentifier(n)) {
			throw new CmdLineException(owner, "\"" + n + "\" is not a valid identifier", null);
		}
		return n;
	}

	public String getValue(String v) throws CmdLineException {
		if (v.startsWith("@")) {
			File f = new File(v.substring(1));
			try {
				return readTextFile(f).trim();
			} catch (IOException e) {
				throw new CmdLineException(owner, "Failed to read @file", e);
			}
		}
		return v;
	}

	public boolean isIdentifier(String n) {
		if (n == null || n.isEmpty()) {
			return false;
		}

		for (int i = 0; i < n.length();) {
			int c = n.codePointAt(i);

			if (i == 0) {
				if (!Character.isUnicodeIdentifierStart(c))
					return false;
			} else {
				if (!Character.isUnicodeIdentifierPart(c))
					return false;
			}

			i += Character.charCount(c);
		}

		return true;
	}

	@Override
	protected Map createNewCollection(Class<? extends Map> type) {
		return new LinkedHashMap(); // make sure to preserve order of arguments
	}

}
