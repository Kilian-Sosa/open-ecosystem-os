package com.openecosystem.os.drive;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drive")
public class DriveTrashController {
  private final DriveTrashService service;

  public DriveTrashController(DriveTrashService service) {
    this.service = service;
  }

  @GetMapping("/trash")
  public List<DriveTrashResponse> listTrash() {
    return service.listTrash();
  }

  @PostMapping("/trash/{groupId}/restore")
  public DriveTrashResponse restore(
      @PathVariable String groupId, @RequestBody(required = false) Map<String, Object> body) {
    boolean supplied = body != null && body.containsKey("destinationFolderId");
    Object destination = supplied ? body.get("destinationFolderId") : null;
    if (destination != null && (!(destination instanceof String value) || value.isBlank()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          ApiErrorCode.BAD_REQUEST,
          "Restore destination must be a folder ID or null");
    return service.restore(groupId, new DriveRestoreDestination(supplied, (String) destination));
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
