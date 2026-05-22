package com.openecosystem.os.drive;

import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/drive/files")
public class DriveFileController {

  private final DriveUploadService driveUploadService;

  public DriveFileController(DriveUploadService driveUploadService) {
    this.driveUploadService = driveUploadService;
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
  public ResponseEntity<DriveFileResponse> uploadFile(@RequestPart("file") MultipartFile file) {
    DriveFileResponse response = driveUploadService.upload(file);
    return ResponseEntity.created(URI.create("/api/drive/files/" + response.fileId()))
        .body(response);
  }
}
