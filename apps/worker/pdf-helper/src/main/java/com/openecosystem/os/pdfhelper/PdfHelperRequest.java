package com.openecosystem.os.pdfhelper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

record PdfHelperRequest(String command, Path input, Path output, int page, int dpi, long maxRenderedPixels) {

  static PdfHelperRequest parse(String[] arguments) {
    if (arguments.length < 1 || !(arguments[0].equals("inspect") || arguments[0].equals("page"))) {
      throw new IllegalArgumentException("Invalid PDF helper request");
    }
    Map<String, String> values = new HashMap<>();
    for (int index = 1; index < arguments.length; index += 2) {
      if (index + 1 >= arguments.length || !arguments[index].startsWith("--")) {
        throw new IllegalArgumentException("Invalid PDF helper request");
      }
      values.put(arguments[index], arguments[index + 1]);
    }
    Path input = Path.of(required(values, "--input")).toAbsolutePath().normalize();
    if (arguments[0].equals("inspect")) return new PdfHelperRequest("inspect", input, null, 0, 0, 0);
    Path output = Path.of(required(values, "--output")).toAbsolutePath().normalize();
    int page = positive(values, "--page");
    int dpi = positive(values, "--dpi");
    long pixels = positiveLong(values, "--max-rendered-pixels");
    if (!"1".equals(required(values, "--protocol-version"))) {
      throw new IllegalArgumentException("Invalid PDF helper request");
    }
    return new PdfHelperRequest("page", input, output, page, dpi, pixels);
  }

  private static String required(Map<String, String> values, String key) {
    String value = values.get(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid PDF helper request");
    return value;
  }

  private static int positive(Map<String, String> values, String key) {
    try { int value = Integer.parseInt(required(values, key)); if (value > 0) return value; } catch (NumberFormatException ignored) { }
    throw new IllegalArgumentException("Invalid PDF helper request");
  }

  private static long positiveLong(Map<String, String> values, String key) {
    try { long value = Long.parseLong(required(values, key)); if (value > 0) return value; } catch (NumberFormatException ignored) { }
    throw new IllegalArgumentException("Invalid PDF helper request");
  }
}
