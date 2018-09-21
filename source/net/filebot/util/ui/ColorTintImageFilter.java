
package net.filebot.util.ui;


import java.awt.Color;
import java.awt.image.RGBImageFilter;


public class ColorTintImageFilter extends RGBImageFilter {

	private Color color;
	private float intensity;


	public ColorTintImageFilter(Color color, float intensity) {
		this.color = color;
		this.intensity = intensity;

		canFilterIndexColorModel = true;
	}


	@Override
	public int filterRGB(int x, int y, int rgb) {
		Color c = new Color(rgb, true);

		int red = (int) ((c.getRed() * (1 - intensity)) + color.getRed() * intensity);
		int green = (int) ((c.getGreen() * (1 - intensity)) + color.getGreen() * intensity);
		int blue = (int) ((c.getBlue() * (1 - intensity)) + color.getBlue() * intensity);

		return new Color(red, green, blue, c.getAlpha()).getRGB();
	}

}
