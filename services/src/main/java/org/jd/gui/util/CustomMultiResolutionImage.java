package org.jd.gui.util;

import java.awt.Image;
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
			images[i] = new BufferedImage(baseWidth + 2 * i, baseHeight + 2 * i, BufferedImage.TYPE_INT_ARGB);
			images[i].getGraphics().drawImage(baseImage, i, i, null);
		}
		return images;
	}

	@Override
	public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
		return super.getResolutionVariant(Math.round(destImageWidth), Math.round(destImageHeight));
	}
}