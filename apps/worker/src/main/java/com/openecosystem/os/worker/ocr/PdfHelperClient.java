package com.openecosystem.os.worker.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PdfHelperClient {

  private static final int STDERR_LIMIT_BYTES = 4 * 1024;
  private final BoundedProcessRunner processRunner;
  private final WorkerOcrProperties properties;
  private final ObjectMapper objectMapper;

  public PdfHelperClient(
      BoundedProcessRunner processRunner,
      WorkerOcrProperties properties,
      ObjectMapper objectMapper) {
    this.processRunner = processRunner;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public int inspect(Path pdf, OcrExecutionDeadline deadline) {
    JsonNode response =
        run(
            List.of(
                properties.pdfHelperCommand(),
                "-jar",
                properties.pdfHelperJar(),
                "inspect",
                "--input",
                pdf.toString()),
            deadline);
    int pages = response.path("pageCount").asInt(0);
    if (response.path("protocolVersion").asInt() != 1 || pages <= 0) throw protocolFailure();
    return pages;
  }

  public PdfHelperPage processPage(
      Path pdf, Path renderedOutput, int pageNumber, OcrExecutionDeadline deadline) {
    JsonNode response =
        run(
            List.of(
                properties.pdfHelperCommand(),
                "-jar",
                properties.pdfHelperJar(),
                "page",
                "--input",
                pdf.toString(),
                "--output",
                renderedOutput.toString(),
                "--page",
                Integer.toString(pageNumber),
                "--dpi",
                Integer.toString(properties.renderDpi()),
                "--max-rendered-pixels",
                Long.toString(properties.maxRenderedPixels()),
                "--protocol-version",
                "1"),
            deadline);
    if (response.path("protocolVersion").asInt() != 1) throw protocolFailure();
    String decision = response.path("decision").asText();
    if ("TESSERACT".equals(decision)) return PdfHelperPage.tesseract();
    if (!"NATIVE".equals(decision) || !response.has("pageText") || !response.has("words"))
      throw protocolFailure();
    List<OcrWord> words = new ArrayList<>();
    int pageWordOrder = 1;
    for (JsonNode node : response.path("words")) {
      if (pageWordOrder > properties.maxWordsPerPage()) {
        throw new OcrProviderException(
            "OCR_WORD_LIMIT", "OCR result exceeded the configured word limit");
      }
      JsonNode box = node.path("boundingBox");
      if (node.path("text").asText().isBlank() || !box.isObject()) throw protocolFailure();
      words.add(
          new OcrWord(
              1,
              pageNumber,
              pageWordOrder++,
              node.path("blockNumber").asInt(),
              node.path("paragraphNumber").asInt(),
              node.path("lineNumber").asInt(),
              node.path("wordNumber").asInt(),
              node.path("text").asText(),
              null,
              new OcrBoundingBox(
                  box.path("left").asInt(),
                  box.path("top").asInt(),
                  box.path("width").asInt(),
                  box.path("height").asInt())));
    }
    return new PdfHelperPage(
        OcrSourceKind.PDF_TEXT_LAYER, response.path("pageText").asText(), words);
  }

  private JsonNode run(List<String> command, OcrExecutionDeadline deadline) {
    try {
      BoundedProcessResult result =
          processRunner.run(
              command,
              deadline.remaining(),
              properties.maxProcessOutputBytes(),
              STDERR_LIMIT_BYTES);
      if (result.exitCode() != 0)
        throw new OcrProviderException("OCR_PDF_HELPER_FAILED", "OCR PDF helper failed");
      return objectMapper.readTree(result.stdout());
    } catch (OcrProviderException exception) {
      throw exception.code().equals("OCR_PROCESS_TIMEOUT")
          ? new OcrProviderException("OCR_DOCUMENT_TIMEOUT", "OCR document deadline was exceeded")
          : exception;
    } catch (Exception exception) {
      throw protocolFailure();
    }
  }

  private OcrProviderException protocolFailure() {
    return new OcrProviderException(
        "OCR_PDF_HELPER_PROTOCOL_INVALID", "OCR PDF helper response was invalid");
  }
}
