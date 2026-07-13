package com.openecosystem.os.pdfhelper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

final class PdfFixtures {

  private PdfFixtures() {}

  static Path bornDigitalMultiline(Path root) throws IOException {
    Path pdf = root.resolve("born-digital-multiline.pdf");
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(48, 720);
        stream.showText("Invoice Number INV-2026-42");
        stream.newLineAtOffset(0, -24);
        stream.showText("Issue Date 2026-07-13");
        stream.newLineAtOffset(0, -24);
        stream.showText("Total Amount EUR 129.90");
        stream.endText();
      }
      document.save(pdf.toFile());
    }
    return pdf;
  }

  static Path scannedWithFooter(Path root) throws IOException {
    Path png = root.resolve("scanned-invoice.png");
    BufferedImage raster = new BufferedImage(400, 500, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D graphics = raster.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 400, 500);
    graphics.setColor(Color.BLACK);
    graphics.drawString("INVOICE 42", 20, 30);
    graphics.dispose();
    ImageIO.write(raster, "png", png.toFile());
    Path pdf = root.resolve("scanned-with-footer.pdf");
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.drawImage(LosslessFactory.createFromImage(document, raster), 20, 100, 572, 650);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        stream.newLineAtOffset(40, 40);
        stream.showText("Accessibility footer with more than twenty characters");
        stream.endText();
      }
      document.save(pdf.toFile());
    }
    return pdf;
  }
}
