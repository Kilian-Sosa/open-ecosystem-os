package com.openecosystem.os.drive;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drive")
public class DriveTrashController {
  private final DriveTrashService service;

  public DriveTrashController(DriveTrashService service) {
    this.service = service;
  }

  @PostMapping("/files/{fileId}/trash")
  public DriveTrashMutationResponse trashFile(@PathVariable String fileId) {
    return DriveTrashMutationResponse.from(service.trashFile(fileId));
  }

  @PostMapping("/folders/{folderId}/trash")
  public DriveTrashMutationResponse trashFolder(@PathVariable String folderId) {
    return DriveTrashMutationResponse.from(service.trashFolder(folderId));
  }
}
