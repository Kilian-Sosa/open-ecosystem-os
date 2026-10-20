package com.openecosystem.os.drive;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DriveFileContentService {
  private static final long MAX_SOURCE_BYTES = 25L * 1024 * 1024;
  private static final Set<String> PREVIEW_TYPES =
      Set.of("application/pdf", "image/png", "image/jpeg", "text/plain");
  private final AuthenticationContext authentication;
  private final ResourceAuthorizationService authorization;
  private final DriveFileRepository repository;
  private final FileObjectStorage storage;
  private final FileEncryptionService encryption;
  private final DriveProperties properties;
  private final DriveUploadContentValidator validator;
  private final Semaphore pdfPreviewSlot = new Semaphore(1);

  public DriveFileContentService(
      AuthenticationContext authentication,
      ResourceAuthorizationService authorization,
      DriveFileRepository repository,
      FileObjectStorage storage,
      FileEncryptionService encryption,
      DriveProperties properties,
      DriveUploadContentValidator validator) {
    this.authentication = authentication;
    this.authorization = authorization;
    this.repository = repository;
    this.storage = storage;
    this.encryption = encryption;
    this.properties = properties;
    this.validator = validator;
  }

  public DriveFileContent read(String fileId) {
    var actor = authentication.currentPrincipal();
    if (!authorization
        .decide(actor, actor.workspaceId(), ResourceType.FILE, fileId, ResourceAction.VIEW)
        .allowed()) {
      throw notFound();
    }
    var metadata =
        repository.findByIdForWorkspace(fileId, actor.workspaceId()).orElseThrow(this::notFound);
    try {
      if (metadata.sizeBytes() <= 0
          || metadata.sizeBytes() > Math.min(properties.maxUploadBytes(), MAX_SOURCE_BYTES)
          || !FileEncryptionService.ALGORITHM.equals(metadata.encryptionAlgorithm())
          || !properties.encryption().keyId().equals(metadata.encryptionKeyId())) {
        throw new IllegalStateException();
      }
      byte[] ciphertext =
          storage.getEncryptedObject(metadata.storageKey(), metadata.sizeBytes() + 16);
      if (ciphertext.length != metadata.sizeBytes() + 16) throw new IllegalStateException();
      byte[] bytes = encryption.decryptBytes(ciphertext, metadata.contentIv());
      if (bytes.length != metadata.sizeBytes()
          || !sha256(bytes).equals(metadata.checksumSha256())) {
        throw new IllegalStateException();
      }
      return new DriveFileContent(
          bytes,
          encryption.decryptText(metadata.encryptedName(), metadata.nameIv()),
          metadata.contentType());
    } catch (RuntimeException exception) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ApiErrorCode.INTERNAL_ERROR,
          "Drive file content could not be read");
    }
  }

  public DriveFileContent preview(String fileId) {
    DriveFileContent original = read(fileId);
    if (!PREVIEW_TYPES.contains(original.contentType())) {
      throw new ApiException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          ApiErrorCode.BAD_REQUEST,
          "Preview is unavailable for this file type");
    }
    boolean pdf = original.contentType().equals("application/pdf");
    if (pdf && !pdfPreviewSlot.tryAcquire()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ApiErrorCode.INTERNAL_ERROR,
          "Preview is busy; try again later");
    }
    try {
      validator.validate(original.bytes(), original.contentType());
      return pdf
          ? new DriveFileContent(
              rasterizedPdf(original.bytes()), original.name(), original.contentType())
          : original;
    } catch (IOException | RuntimeException exception) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          ApiErrorCode.BAD_REQUEST,
          "Drive file cannot be previewed safely");
    } finally {
      if (pdf) pdfPreviewSlot.release();
    }
  }

  private byte[] rasterizedPdf(byte[] bytes) throws IOException {
    long started = System.nanoTime();
    try (var source = Loader.loadPDF(bytes);
        var result = new PDDocument()) {
      if (source.getNumberOfPages() > 40) throw new IOException();
      long pixels = 0;
      for (var page : source.getPages()) {
        float width = page.getCropBox().getWidth(), height = page.getCropBox().getHeight();
        if (!Float.isFinite(width)
            || !Float.isFinite(height)
            || width < 1
            || height < 1
            || width > 8192
            || height > 8192
            || Math.ceil(width) * Math.ceil(height) > 4_000_000) throw new IOException();
        pixels += (long) (Math.ceil(width) * Math.ceil(height));
        if (pixels > 32_000_000) throw new IOException();
      }
      var renderer = new PDFRenderer(source);
      renderer.setSubsamplingAllowed(true);
      renderer.setAnnotationsFilter(annotation -> false);
      for (int i = 0; i < source.getNumberOfPages(); i++) {
        if (System.nanoTime() - started > 30_000_000_000L) throw new IOException();
        var image = renderer.renderImageWithDPI(i, 72, ImageType.RGB);
        try {
          var page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
          result.addPage(page);
          try (var content = new PDPageContentStream(result, page)) {
            content.drawImage(
                JPEGFactory.createFromImage(result, image),
                0,
                0,
                image.getWidth(),
                image.getHeight());
          }
        } finally {
          image.flush();
        }
      }
      var output =
          new ByteArrayOutputStream() {
            @Override
            public synchronized void write(byte[] data, int offset, int length) {
              if ((long) count + length > MAX_SOURCE_BYTES) throw new IllegalStateException();
              super.write(data, offset, length);
            }

            @Override
            public synchronized void write(int value) {
              if (count >= MAX_SOURCE_BYTES) throw new IllegalStateException();
              super.write(value);
            }
          };
      result.save(output);
      return output.toByteArray();
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException();
    }
  }

  private ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive file was not found");
  }
}
