package net.filebot.ui.list;

import static net.filebot.media.XattrMetaInfo.*;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.filebot.format.Define;
import net.filebot.format.MediaBindingBean;
import net.filebot.util.EntryList;
import net.filebot.util.FunctionList;

public class IndexedBindingBean extends MediaBindingBean {

	private int i;
	private int from;
	private int to;

	public IndexedBindingBean(Object object, int i, int from, int to, List<?> context) {
		super(getInfoObject(object), getMediaFile(object), getContext(context));
		this.i = i;
		this.from = from;
		this.to = to;
	}

	@Override
	@Define("i")
	public Integer getModelIndex() {
		return i;
	}

	@Define("from")
	public Integer getFromIndex() {
		return from;
	}

	@Define("to")
	public Integer getToIndex() {
		return to;
	}

	private static Object getInfoObject(Object object) {
		if (object instanceof File) {
			File file = (File) object;
			Object metaInfo = xattr.getMetaInfo(file);
			if (metaInfo != null) {
				return metaInfo;
			}
		}
		return object;
	}

	private static File getMediaFile(Object object) {
		return object instanceof File ? (File) object : new File(object.toString());
	}

	private static Map<File, Object> getContext(List<?> context) {
		List<Object> values = new FunctionList<Object, Object>((List<Object>) context, IndexedBindingBean::getInfoObject);
		List<File> files = new FunctionList<Object, File>((List<Object>) context, IndexedBindingBean::getMediaFile);

		return new EntryList<File, Object>(files, values);
	}

}
