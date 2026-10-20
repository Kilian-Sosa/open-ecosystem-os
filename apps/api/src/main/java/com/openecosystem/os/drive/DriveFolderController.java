package com.openecosystem.os.drive;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

  public DriveFolderController(DriveFolderService service) {
    this.service = service;
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
}
