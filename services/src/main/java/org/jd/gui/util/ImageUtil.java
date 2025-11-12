package org.jd.gui.util;

import java.awt.Image;
import java.awt.Window;
import java.io.IOException;
import java.util.stream.Stream;

import javax.swing.ImageIcon;

public final class ImageUtil {

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

    public static void addJDIconsToFrame(Window window) {
        window.setIconImages(Stream.of(32, 64, 128).map(ImageUtil::getAppIconPath).map(ImageUtil::getImage).toList());
    }

    public static String getAppIconPath(int size) {
        return "/org/jd/gui/images/jd_icon_" + size + ".png";
    }
}
