package com.openecosystem.os.pdfhelper;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class PdfHelperMain {

  private PdfHelperMain() {}

  public static void main(String[] arguments) {
    try {
      PdfHelperRequest request = PdfHelperRequest.parse(arguments);
      PdfPageAnalyzer analyzer = new PdfPageAnalyzer(request.dpi(), request.maxRenderedPixels());
      PdfHelperResponse response =
          request.command().equals("inspect")
              ? PdfHelperResponse.inspection(analyzer.inspect(request.input()))
              : analyzer.analyze(request.input(), request.page(), request.output());
      System.out.print(new ObjectMapper().writeValueAsString(response));
    } catch (Exception ignored) {
      System.exit(2);
    }
  }
}
