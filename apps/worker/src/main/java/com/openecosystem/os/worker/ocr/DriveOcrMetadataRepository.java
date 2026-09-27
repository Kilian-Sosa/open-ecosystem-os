package com.openecosystem.os.worker.ocr;

import java.util.Optional;

public interface DriveOcrMetadataRepository {

  Optional<DriveOcrFileMetadata> findByFileIdAndWorkspaceId(String fileId, String workspaceId);
}
