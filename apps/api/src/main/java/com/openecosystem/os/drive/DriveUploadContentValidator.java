package com.openecosystem.os.drive;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DriveUploadContentValidator {
  private static final int MAX_SOURCE_BYTES = 25 * 1024 * 1024;
  private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
  private static final long MAX_EXPANDED_BYTES = 64L * 1024 * 1024;
  private static final Map<String, String> OFFICE_PARTS =
      Map.of(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "word/document.xml",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xl/workbook.xml",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
              "ppt/presentation.xml");

  public void validate(byte[] bytes, String declaredContentType) {
    try {
      require(bytes != null && bytes.length > 0 && bytes.length <= MAX_SOURCE_BYTES);
      switch (declaredContentType) {
        case "application/pdf" -> pdf(bytes);
        case "image/png" -> {
          png(bytes);
          image(bytes, "png");
        }
        case "image/jpeg" -> {
          require(
              bytes.length > 4
                  && (bytes[0] & 255) == 255
                  && (bytes[1] & 255) == 216
                  && (bytes[bytes.length - 2] & 255) == 255
                  && (bytes[bytes.length - 1] & 255) == 217);
          image(bytes, "JPEG");
        }
        case "text/plain" -> text(bytes);
        default -> {
          String part = OFFICE_PARTS.get(declaredContentType);
          require(part != null);
          office(bytes, part, declaredContentType);
        }
      }
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          ApiErrorCode.BAD_REQUEST,
          "Uploaded file content is invalid or does not match its declared type");
    }
  }

  private void pdf(byte[] bytes) throws IOException {
    require(
        new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.US_ASCII)
            .matches("%PDF-1\\.[0-7]|%PDF-2\\.0"));
    require(
        new String(
                bytes,
                Math.max(0, bytes.length - 32),
                Math.min(bytes.length, 32),
                StandardCharsets.US_ASCII)
            .stripTrailing()
            .endsWith("%%EOF"));
    var parser = new PDFParser(new RandomAccessReadBuffer(bytes));
    try (var document = parser.parse(false)) {
      require(
          !document.isEncrypted()
              && document.getNumberOfPages() > 0
              && document.getNumberOfPages() <= 2000);
      long expanded = 0;
      for (var page : document.getPages()) {
        require(page.getMediaBox().getWidth() > 0 && page.getMediaBox().getHeight() > 0);
        var streams = page.getContentStreams();
        while (streams.hasNext()) {
          try (var stream = streams.next().createInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
              expanded += count;
              require(expanded <= MAX_EXPANDED_BYTES);
            }
          }
        }
      }
    }
  }

  private void image(byte[] bytes, String format) throws IOException {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      require(readers.hasNext());
      ImageReader reader = readers.next();
      try {
        require(reader.getFormatName().equalsIgnoreCase(format));
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        require(width > 0 && height > 0 && (long) width * height <= 16_000_000);
        final boolean[] warning = {false};
        reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
        BufferedImage image = reader.read(0);
        require(image != null && !warning[0]);
      } finally {
        reader.dispose();
      }
    }
  }

  private void png(byte[] bytes) throws IOException {
    var data = new DataInputStream(new ByteArrayInputStream(bytes));
    require(data.readLong() == 0x89504e470d0a1a0aL);
    boolean header = false, content = false, end = false;
    int chunks = 0;
    while (data.available() > 0) {
      require(++chunks <= 4096 && !end);
      int length = data.readInt();
      require(length >= 0 && length <= data.available() - 8);
      byte[] type = data.readNBytes(4), payload = data.readNBytes(length);
      CRC32 crc = new CRC32();
      crc.update(type);
      crc.update(payload);
      require((int) crc.getValue() == data.readInt());
      String name = new String(type, StandardCharsets.US_ASCII);
      if (!header) {
        require(name.equals("IHDR") && length == 13);
        header = true;
      } else require(!name.equals("IHDR"));
      if (name.equals("IDAT")) content = true;
      if (name.equals("IEND")) {
        require(length == 0 && content);
        end = true;
      }
    }
    require(header && content && end);
  }

  private void text(byte[] bytes) throws CharacterCodingException {
    String value =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    require(
        value
            .codePoints()
            .noneMatch(c -> Character.isISOControl(c) && c != 10 && c != 13 && c != 9));
    String start = value.startsWith("\ufeff") ? value.substring(1) : value;
    require(!start.startsWith("%PDF-") && !start.startsWith("PK\u0003\u0004"));
  }

  private void office(byte[] bytes, String part, String mime) throws Exception {
    require(bytes.length >= 22);
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int end = -1;
    for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (buffer.getInt(i) == 0x06054b50
          && i + 22 + Short.toUnsignedInt(buffer.getShort(i + 20)) == bytes.length) {
        end = i;
        break;
      }
    }
    require(end >= 0 && buffer.getShort(end + 4) == 0 && buffer.getShort(end + 6) == 0);
    int count = Short.toUnsignedInt(buffer.getShort(end + 10));
    require(count > 0 && count <= 128 && Short.toUnsignedInt(buffer.getShort(end + 8)) == count);
    long size = Integer.toUnsignedLong(buffer.getInt(end + 12)),
        offset = Integer.toUnsignedLong(buffer.getInt(end + 16));
    require(offset + size == end && size >= 46L * count);
    int cursor = (int) offset;
    Set<String> centralNames = new HashSet<>();
    Map<String, ZipFacts> centralEntries = new HashMap<>();
    for (int i = 0; i < count; i++) {
      require(cursor + 46 <= end && buffer.getInt(cursor) == 0x02014b50);
      int flags = Short.toUnsignedInt(buffer.getShort(cursor + 8));
      require((flags & 1) == 0);
      int nameLength = Short.toUnsignedInt(buffer.getShort(cursor + 28)),
          extra = Short.toUnsignedInt(buffer.getShort(cursor + 30)),
          comment = Short.toUnsignedInt(buffer.getShort(cursor + 32));
      require(cursor + 46 + nameLength + extra + comment <= end);
      String name = new String(bytes, cursor + 46, nameLength, StandardCharsets.UTF_8);
      require(centralNames.add(name));
      centralEntries.put(
          name,
          new ZipFacts(
              Integer.toUnsignedLong(buffer.getInt(cursor + 16)),
              Integer.toUnsignedLong(buffer.getInt(cursor + 20)),
              Integer.toUnsignedLong(buffer.getInt(cursor + 24))));
      cursor += 46 + nameLength + extra + comment;
    }
    require(cursor == end);
    Map<String, byte[]> xml = new HashMap<>();
    Set<String> names = new HashSet<>();
    long expanded = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        require(
            names.size() < 128
                && names.add(name)
                && centralNames.contains(name)
                && !name.startsWith("/")
                && !name.contains("\\")
                && !Arrays.asList(name.split("/")).contains(".."));
        byte[] data = zip.readNBytes(MAX_ENTRY_BYTES + 1);
        require(data.length <= MAX_ENTRY_BYTES);
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipFacts expected = centralEntries.get(name);
        require(expected.crc() == crc.getValue() && expected.size() == data.length);
        expanded += data.length;
        require(expanded <= MAX_EXPANDED_BYTES);
        if (name.endsWith(".xml") || name.endsWith(".rels")) {
          xml(data);
          xml.put(name, data);
        }
        require(!name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"));
        zip.closeEntry();
        require(expected.compressedSize() == entry.getCompressedSize());
      }
    }
    require(
        names.equals(centralNames)
            && xml.containsKey("[Content_Types].xml")
            && xml.containsKey("_rels/.rels")
            && xml.containsKey(part));
    String root =
        part.startsWith("word") ? "document" : part.startsWith("xl") ? "workbook" : "presentation";
    String family =
        part.startsWith("word")
            ? "wordprocessingml"
            : part.startsWith("xl") ? "spreadsheetml" : "presentationml";
    require(
        xml(xml.get(part))
            .root()
            .equals("{http://schemas.openxmlformats.org/" + family + "/2006/main}" + root));
    var types = xml(xml.get("[Content_Types].xml"));
    require(
        types.root().equals("{http://schemas.openxmlformats.org/package/2006/content-types}Types"));
    require(types.values().contains("Override:/" + part + ":" + mime + ".main+xml"));
    var rels = xml(xml.get("_rels/.rels"));
    require(
        rels.root()
            .equals("{http://schemas.openxmlformats.org/package/2006/relationships}Relationships"));
    require(
        rels.values()
            .contains(
                "Relationship:http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument:"
                    + part));
  }

  private XmlFacts xml(byte[] bytes) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    factory.setXMLResolver(
        (publicId, systemId, base, namespace) -> {
          throw new XMLStreamException("External XML is forbidden");
        });
    var reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
    int depth = 0, elements = 0;
    String root = null;
    Set<String> values = new HashSet<>();
    try {
      while (reader.hasNext()) {
        int event = reader.next();
        require(event != XMLStreamConstants.DTD && event != XMLStreamConstants.ENTITY_REFERENCE);
        if (event == XMLStreamConstants.START_ELEMENT) {
          require(++depth <= 128 && ++elements <= 100000);
          if (root == null) root = reader.getName().toString();
          String local = reader.getLocalName(), namespace = reader.getNamespaceURI();
          if (depth == 2
              && local.equals("Override")
              && "http://schemas.openxmlformats.org/package/2006/content-types".equals(namespace))
            values.add(
                "Override:"
                    + reader.getAttributeValue(null, "PartName")
                    + ":"
                    + reader.getAttributeValue(null, "ContentType"));
          if (depth == 2
              && local.equals("Relationship")
              && "http://schemas.openxmlformats.org/package/2006/relationships".equals(namespace)) {
            require(!"External".equals(reader.getAttributeValue(null, "TargetMode")));
            values.add(
                "Relationship:"
                    + reader.getAttributeValue(null, "Type")
                    + ":"
                    + reader.getAttributeValue(null, "Target"));
          }
        } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
      }
      require(root != null && depth == 0);
      return new XmlFacts(root, values);
    } finally {
      reader.close();
    }
  }

  private record XmlFacts(String root, Set<String> values) {}

  private record ZipFacts(long crc, long compressedSize, long size) {}

  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("Invalid content");
  }
}
