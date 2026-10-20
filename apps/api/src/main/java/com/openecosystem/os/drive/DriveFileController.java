package com.openecosystem.os.drive;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/drive/files")
public class DriveFileController {

  private final DriveUploadService driveUploadService;
  private final DriveFileContentService contentService;
  private final DriveFileMutationService mutationService;

  public DriveFileController(
      DriveUploadService driveUploadService,
      DriveFileContentService contentService,
      DriveFileMutationService mutationService) {
    this.driveUploadService = driveUploadService;
    this.contentService = contentService;
    this.mutationService = mutationService;
  }

  @PatchMapping("/{fileId}/name")
  public DriveFileResponse renameFile(
      @PathVariable String fileId, @RequestBody RenameFileRequest request) {
    return mutationService.renameFile(fileId, request.name());
  }

  @PatchMapping("/{fileId}/location")
  public DriveFileResponse moveFile(
      @PathVariable String fileId, @RequestBody MoveFileRequest request) {
    return mutationService.moveFile(fileId, request.destinationFolderId());
  }

  public record RenameFileRequest(String name) {}

  public record MoveFileRequest(String destinationFolderId) {}

  @GetMapping("/{fileId}/download")
  public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {
    return contentResponse(contentService.read(fileId), false);
  }

  @GetMapping("/{fileId}/preview")
  public ResponseEntity<byte[]> previewFile(@PathVariable String fileId) {
    return contentResponse(contentService.preview(fileId), true);
  }

  private ResponseEntity<byte[]> contentResponse(DriveFileContent content, boolean inline) {
    String filename = content.name();
    if (filename == null
        || filename.isBlank()
        || filename.length() > 255
        || filename.equals(".")
        || filename.equals("..")
        || filename
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) || "/\\\"<>:|?*".indexOf(c) >= 0)) {
      filename = "download";
    }
    MediaType type;
    try {
      type = MediaType.parseMediaType(content.contentType());
      if (type.isWildcardType() || type.isWildcardSubtype())
        type = MediaType.APPLICATION_OCTET_STREAM;
    } catch (IllegalArgumentException exception) {
      type = MediaType.APPLICATION_OCTET_STREAM;
    }
    if (inline && type.equals(MediaType.TEXT_PLAIN))
      type = new MediaType("text", "plain", StandardCharsets.UTF_8);
    String disposition =
        (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(type)
        .contentLength(content.bytes().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'self'")
        .header("Referrer-Policy", "no-referrer")
        .body(content.bytes());
  }

  @GetMapping
  public DriveFileListResponse listFiles() {
    return driveUploadService.listFiles();
  }

  @GetMapping("/{fileId}")
  public DriveFileResponse getFile(@PathVariable String fileId) {
    return driveUploadService.getFile(fileId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DriveFileResponse> uploadFile(
      @RequestPart("file") MultipartFile file, @RequestParam(required = false) String folderId) {
    DriveFileResponse response = driveUploadService.upload(file, folderId);
    return ResponseEntity.created(URI.create("/api/drive/files/" + response.fileId()))
        .body(response);
  }
}
