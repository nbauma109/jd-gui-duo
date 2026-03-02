package org.jd.gui.util;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

public class CustomMultiResolutionImage extends BaseMultiResolutionImage {

    private static final int DEFAULT_IMAGE_COUNT = 8;

    private static final double[] DEFAULT_SCALE_FACTORS = {
        1.0d, 1.25d, 1.5d, 1.75d, 2.0d, 2.5d, 3.0d, 4.0d
    };

    private static final double UPSCALE_STEP_FACTOR = 1.5d;

    public CustomMultiResolutionImage(Image baseImage, int imageCount) {
        super(createVariants(requireValidBaseImage(baseImage), requireValidImageCount(imageCount)));
    }

    public CustomMultiResolutionImage(Image baseImage) {
        this(baseImage, DEFAULT_IMAGE_COUNT);
    }

    public CustomMultiResolutionImage(String iconPath) throws IOException {
        this(iconPath, DEFAULT_IMAGE_COUNT);
    }

    public CustomMultiResolutionImage(String iconPath, int imageCount) throws IOException {
        this(loadImageFromResource(iconPath), imageCount);
    }

    private static int requireValidImageCount(int imageCount) {
        if (imageCount <= 0) {
            throw new IllegalArgumentException("imageCount must be positive");
        }
        return imageCount;
    }

    private static Image requireValidBaseImage(Image baseImage) {
        Objects.requireNonNull(baseImage, "baseImage");
        int width = baseImage.getWidth(null);
        int height = baseImage.getHeight(null);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("baseImage must be fully loaded and have positive dimensions");
        }
        return baseImage;
    }

    private static Image loadImageFromResource(String iconPath) throws IOException {
        Objects.requireNonNull(iconPath, "iconPath");
        URL resourceUrl = CustomMultiResolutionImage.class.getResource(iconPath);
        if (resourceUrl == null) {
            throw new IOException("Resource not found on classpath: " + iconPath);
        }
        Image image = ImageIO.read(resourceUrl);
        if (image == null) {
            throw new IOException("Unsupported or unreadable image resource: " + iconPath);
        }
        return image;
    }

    private static Image[] createVariants(Image baseImage, int imageCount) {
        BufferedImage baseBuffered = toBufferedImage(baseImage);

        int baseWidth = baseBuffered.getWidth();
        int baseHeight = baseBuffered.getHeight();

        int limit = Math.min(imageCount, DEFAULT_SCALE_FACTORS.length);

        Map<Dimension, BufferedImage> variants = new LinkedHashMap<>();
        variants.put(new Dimension(baseWidth, baseHeight), baseBuffered);

        for (int i = 0; i < limit; i++) {
            double scale = DEFAULT_SCALE_FACTORS[i];
            int targetWidth = positiveRoundedDimension(baseWidth * scale);
            int targetHeight = positiveRoundedDimension(baseHeight * scale);
            Dimension target = new Dimension(targetWidth, targetHeight);

            if (variants.containsKey(target)) {
                continue;
            }

            BufferedImage scaled = scaleProgressivelyBicubic(baseBuffered, targetWidth, targetHeight);
            variants.put(target, scaled);
        }

        List<Image> out = new ArrayList<>(variants.size());
        out.addAll(variants.values());
        return out.toArray(new Image[0]);
    }

    private static int positiveRoundedDimension(double value) {
        long rounded = Math.round(value);
        long clamped = Math.clamp(rounded, 1L, Integer.MAX_VALUE);
        return (int) clamped;
    }

    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage im) {
            return im;
        }

        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image must be fully loaded and have positive dimensions");
        }

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffered.createGraphics();
        try {
            applyQualityHints(graphics);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return buffered;
    }

    private static BufferedImage scaleProgressivelyBicubic(BufferedImage source, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("target dimensions must be positive");
        }

        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();

        if (currentWidth == targetWidth && currentHeight == targetHeight) {
            return source;
        }

        BufferedImage current = source;

        while (currentWidth != targetWidth || currentHeight != targetHeight) {
            int nextWidth = nextUpscaleDimension(currentWidth, targetWidth);
            int nextHeight = nextUpscaleDimension(currentHeight, targetHeight);

            BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = next.createGraphics();
            try {
                applyQualityHints(graphics);
                graphics.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            } finally {
                graphics.dispose();
            }

            current = next;
            currentWidth = nextWidth;
            currentHeight = nextHeight;
        }

        return current;
    }

    private static int nextUpscaleDimension(int current, int target) {
        if (current >= target) {
            return target;
        }

        int stepped = (int) Math.round(current * UPSCALE_STEP_FACTOR);
        if (stepped <= current) {
            stepped = current + 1;
        }
        return Math.min(target, stepped);
    }

    private static void applyQualityHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    }

    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        int safeWidth = positiveRoundedDimension(destImageWidth);
        int safeHeight = positiveRoundedDimension(destImageHeight);
        return super.getResolutionVariant(safeWidth, safeHeight);
    }
}