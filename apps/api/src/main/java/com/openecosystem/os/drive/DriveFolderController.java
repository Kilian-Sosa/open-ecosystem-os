package com.openecosystem.os.drive;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drive/folders")
public class DriveFolderController {
  private final DriveFolderService service;
  private final DriveArchiveService archives;

  public DriveFolderController(DriveFolderService service, DriveArchiveService archives) {
    this.service = service;
    this.archives = archives;
  }

  @GetMapping("/{folderId}/download")
  public void downloadFolder(@PathVariable String folderId, HttpServletResponse response)
      throws IOException {
    try (var archive = archives.prepareZip(folderId)) {
      response.setContentType("application/zip");
      response.setContentLengthLong(Files.size(archive.path()));
      response.setHeader(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment()
              .filename(archive.filename(), StandardCharsets.UTF_8)
              .build()
              .toString());
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader(
          "Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'self'");
      response.setHeader("Referrer-Policy", "no-referrer");
      try (var input = Files.newInputStream(archive.path())) {
        input.transferTo(response.getOutputStream());
      }
    }
  }

  @PostMapping
  public ResponseEntity<DriveFolderResponse> createFolder(
      @RequestBody CreateFolderRequest request) {
    var response = service.createFolder(request.name(), request.parentFolderId());
    return ResponseEntity.created(URI.create("/api/drive/folders/" + response.folderId()))
        .body(response);
  }

  @GetMapping("/contents")
  public DriveDirectoryListResponse listFolderContents(
      @RequestParam(required = false) String folderId) {
    return service.listFolderContents(folderId);
  }

  @GetMapping("/{folderId}")
  public DriveFolderResponse getFolder(@PathVariable String folderId) {
    return service.getFolder(folderId);
  }

  public record CreateFolderRequest(String name, String parentFolderId) {}

  @PatchMapping("/{folderId}/name")
  public DriveFolderResponse renameFolder(
      @PathVariable String folderId, @RequestBody RenameFolderRequest request) {
    return service.renameFolder(folderId, request.name());
  }

  @PatchMapping("/{folderId}/location")
  public DriveFolderResponse moveFolder(
      @PathVariable String folderId, @RequestBody MoveFolderRequest request) {
    return service.moveFolder(folderId, request.destinationFolderId());
  }

  public record RenameFolderRequest(String name) {}

  public record MoveFolderRequest(String destinationFolderId) {}
}
