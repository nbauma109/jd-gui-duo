package org.jd.gui.util;

import java.awt.Image;
import java.io.IOException;

import javax.swing.ImageIcon;

public class ImageUtil {

	private ImageUtil() {
	}

	public static ImageIcon newImageIcon(String iconPath) {
		return new ImageIcon(getImage(iconPath));
	}

	public static Image getImage(String iconPath) {
		try {
			return new CustomMultiResolutionImage(iconPath);
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid icon path : " + iconPath, e);
		}
	}
}
