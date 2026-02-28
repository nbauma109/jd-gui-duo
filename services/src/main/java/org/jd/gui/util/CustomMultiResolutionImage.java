package org.jd.gui.util;

import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

public class CustomMultiResolutionImage extends BaseMultiResolutionImage {

    private static final int DEFAULT_IMAGE_COUNT = 10;

    public CustomMultiResolutionImage(Image baseImage, int imageCount) {
        super(createLargerImages(baseImage, imageCount));
    }

    public CustomMultiResolutionImage(Image baseImage) {
        this(baseImage, DEFAULT_IMAGE_COUNT);
    }

    public CustomMultiResolutionImage(String iconPath) throws IOException {
        this(iconPath, DEFAULT_IMAGE_COUNT);
    }

    public CustomMultiResolutionImage(String iconPath, int imageCount) throws IOException {
        this(ImageIO.read(CustomMultiResolutionImage.class.getResource(iconPath)), imageCount);
    }

    private static Image[] createLargerImages(Image baseImage, int imageCount) {
        int baseWidth = baseImage.getWidth(null);
        int baseHeight = baseImage.getHeight(null);
        Image[] images = new Image[imageCount + 1];
        images[0] = baseImage;
        for (int i = 1; i < images.length; i++) {
            int targetWidth = baseWidth + 2 * i;
            int targetHeight = baseHeight + 2 * i;
            BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(baseImage, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();
            images[i] = scaledImage;
        }
        return images;
    }

    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        return super.getResolutionVariant(Math.round(destImageWidth), Math.round(destImageHeight));
    }
}
