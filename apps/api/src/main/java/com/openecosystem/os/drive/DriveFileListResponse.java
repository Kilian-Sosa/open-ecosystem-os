package com.openecosystem.os.drive;

import java.util.List;

public record DriveFileListResponse(List<DriveFileResponse> files) {

  public DriveFileListResponse {
    files = files == null ? List.of() : List.copyOf(files);
  }
}
