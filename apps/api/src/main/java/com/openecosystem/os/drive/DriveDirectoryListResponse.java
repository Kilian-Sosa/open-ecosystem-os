package com.openecosystem.os.drive;

import java.util.List;

public record DriveDirectoryListResponse(
    List<DriveFolderResponse> folders, List<DriveFileResponse> files) {}
