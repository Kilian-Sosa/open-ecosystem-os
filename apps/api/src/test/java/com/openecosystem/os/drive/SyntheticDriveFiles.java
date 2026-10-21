package com.openecosystem.os.drive;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

final class SyntheticDriveFiles {
  static final String[] TYPES = {
    "application/pdf",
    "image/png",
    "image/jpeg",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  };

  static byte[] pdf() {
    StringBuilder p = new StringBuilder("%PDF-1.4\n");
    int[] offsets = new int[4];
    String[] objs = {
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 20 20] >>"
    };
    for (int i = 1; i <= 3; i++) {
      offsets[i] = p.length();
      p.append(i + " 0 obj\n" + objs[i - 1] + "\nendobj\n");
    }
    int xref = p.length();
    p.append("xref\n0 4\n0000000000 65535 f \n");
    for (int i = 1; i <= 3; i++)
      p.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets[i]));
    p.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
    return p.toString().getBytes(StandardCharsets.US_ASCII);
  }

  static byte[] bytes(String mime) {
    try {
      if (mime.equals(TYPES[0])) return pdf();
      if (mime.startsWith("image/")) {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), mime.substring(6), out);
        return out.toByteArray();
      }
      String path =
          mime.equals(TYPES[3])
              ? "word/document.xml"
              : mime.equals(TYPES[4]) ? "xl/workbook.xml" : "ppt/presentation.xml";
      String root =
          path.startsWith("word")
              ? "document"
              : path.startsWith("xl") ? "workbook" : "presentation";
      String ns =
          path.startsWith("word")
              ? "wordprocessingml"
              : path.startsWith("xl") ? "spreadsheetml" : "presentationml";
      return office(
          path, "<" + root + " xmlns='http://schemas.openxmlformats.org/" + ns + "/2006/main'/>");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static byte[] office(String path, String xml) {
    try {
      String type =
          path.startsWith("word")
              ? "wordprocessingml.document"
              : path.startsWith("xl") ? "spreadsheetml.sheet" : "presentationml.presentation";
      var out = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(out)) {
        entry(
            zip,
            "[Content_Types].xml",
            "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'><Override"
                + " PartName='/"
                + path
                + "' ContentType='application/vnd.openxmlformats-officedocument."
                + type
                + ".main+xml'/></Types>");
        entry(
            zip,
            "_rels/.rels",
            "<Relationships"
                + " xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship"
                + " Id='rId1'"
                + " Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument'"
                + " Target='"
                + path
                + "'/></Relationships>");
        entry(zip, path, xml);
      }
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void entry(ZipOutputStream z, String name, String text) throws Exception {
    z.putNextEntry(new ZipEntry(name));
    z.write(text.getBytes(StandardCharsets.UTF_8));
    z.closeEntry();
  }
}
