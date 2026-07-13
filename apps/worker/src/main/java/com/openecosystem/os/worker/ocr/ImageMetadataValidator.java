package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ImageMetadataValidator {

  public ImageDimensions validate(Path path, String contentType, long maxPixels) {
    if (path == null || maxPixels <= 0) {
      throw invalidDimensions();
    }
    try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
      if (input == null) throw invalidDimensions();
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalidDimensions();
      ImageReader reader = readers.next();
      try {
        if (!matches(reader.getFormatName(), contentType)) throw invalidDimensions();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0) throw invalidDimensions();
        long pixels;
        try {
          pixels = Math.multiplyExact((long) width, (long) height);
        } catch (ArithmeticException exception) {
          throw invalidDimensions();
        }
        if (pixels > maxPixels) {
          throw new OcrProviderException(
              "OCR_IMAGE_PIXEL_LIMIT", "OCR image exceeded the configured pixel limit");
        }
        return new ImageDimensions(width, height);
      } finally {
        reader.dispose();
      }
    } catch (OcrProviderException exception) {
      throw exception;
    } catch (IOException exception) {
      throw invalidDimensions();
    }
  }

  private boolean matches(String formatName, String contentType) {
    String normalized = formatName == null ? "" : formatName.toLowerCase(java.util.Locale.ROOT);
    return ("image/png".equals(contentType) && "png".equals(normalized))
        || ("image/jpeg".equals(contentType)
            && ("jpeg".equals(normalized) || "jpg".equals(normalized)));
  }

  private OcrProviderException invalidDimensions() {
    return new OcrProviderException(
        "OCR_IMAGE_DIMENSIONS_INVALID", "OCR image dimensions were invalid");
  }

  public record ImageDimensions(int width, int height) {}
}
