package net.filebot.ui.subtitle;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;

import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.filebot.subtitle.SubtitleFormat;
import net.miginfocom.swing.MigLayout;

public class SubtitleFileChooser extends JFileChooser {

	protected final JComboBox format = new JComboBox();
	protected final JComboBox encoding = new JComboBox();
	protected final JSpinner offset = new JSpinner(new SpinnerNumberModel(0, -14400000, 14400000, 100));

	public SubtitleFileChooser() {
		setAccessory(createAcessory());
		setDefaultOptions();
	}

	protected void setDefaultOptions() {
		setFormatOptions(singleton(SubtitleFormat.SubRip));

		Set<Charset> encodings = new LinkedHashSet<Charset>(2);
		encodings.add(UTF_8); // UTF-8 as default charset
		encodings.add(Charset.defaultCharset()); // allow default system encoding to be used as well
		setEncodingOptions(encodings);
	}

	protected JComponent createAcessory() {
		JPanel acessory = new JPanel(new MigLayout("nogrid"));

		acessory.add(new JLabel("Encoding:"), "wrap rel");
		acessory.add(encoding, "sg w, wrap para");
		acessory.add(new JLabel("Format:"), "wrap rel");
		acessory.add(format, "sg w, wrap para");
		acessory.add(new JLabel("Timing Offset:"), "wrap rel");
		acessory.add(offset, "wmax 50px");
		acessory.add(new JLabel("ms"));

		return acessory;
	}

	public void setEncodingOptions(Set<Charset> options) {
		encoding.setModel(new DefaultComboBoxModel(options.toArray()));
	}

	public Charset getSelectedEncoding() {
		return (Charset) encoding.getSelectedItem();
	}

	public void setFormatOptions(Set<SubtitleFormat> options) {
		format.setModel(new DefaultComboBoxModel(options.toArray()));
	}

	public SubtitleFormat getSelectedFormat() {
		return (SubtitleFormat) format.getSelectedItem();
	}

	public long getTimingOffset() {
		return (Integer) offset.getValue();
	}
}
