package com.openecosystem.os.media;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OcrJobQueryService {

  private final AuthenticationContext authenticationContext;
  private final OcrJobRepository ocrJobRepository;
  private final DriveFileRepository driveFileRepository;
  private final FileEncryptionService encryptionService;
  private final OcrJobLifecycleProjectionService lifecycleProjectionService;

  public OcrJobQueryService(
      AuthenticationContext authenticationContext,
      OcrJobRepository ocrJobRepository,
      DriveFileRepository driveFileRepository,
      FileEncryptionService encryptionService,
      OcrJobLifecycleProjectionService lifecycleProjectionService) {
    this.authenticationContext = authenticationContext;
    this.ocrJobRepository = ocrJobRepository;
    this.driveFileRepository = driveFileRepository;
    this.encryptionService = encryptionService;
    this.lifecycleProjectionService = lifecycleProjectionService;
  }

  public OcrJobListResponse listJobs() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new OcrJobListResponse(
        ocrJobRepository.listByWorkspace(principal.workspaceId()).stream()
            .map(this::toSummaryResponse)
            .toList());
  }

  public OcrJobDetailResponse getJob(String jobId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return ocrJobRepository
        .findByIdForWorkspace(jobId, principal.workspaceId())
        .map(this::toDetailResponse)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "OCR job was not found"));
  }

  private OcrJobSummaryResponse toSummaryResponse(OcrJob job) {
    return new OcrJobSummaryResponse(
        job.jobId(),
        job.fileId(),
        fileName(job),
        job.contentType(),
        job.status().value(),
        job.provider(),
        job.attemptCount(),
        job.maxAttempts(),
        job.extractedTextLength(),
        DiagnosticFailureSanitizer.code(job.failureCode()),
        DiagnosticFailureSanitizer.ocrReason(job),
        job.correlationId(),
        job.queuedAt(),
        job.processingStartedAt(),
        job.completedAt(),
        job.failedAt(),
        job.updatedAt());
  }

  private OcrJobDetailResponse toDetailResponse(OcrJob job) {
    return new OcrJobDetailResponse(
        job.jobId(),
        job.fileId(),
        fileName(job),
        job.contentType(),
        job.status().value(),
        job.provider(),
        job.attemptCount(),
        job.maxAttempts(),
        job.extractedText(),
        job.extractedTextLength(),
        DiagnosticFailureSanitizer.code(job.failureCode()),
        DiagnosticFailureSanitizer.ocrReason(job),
        job.correlationId(),
        job.queuedAt(),
        job.processingStartedAt(),
        job.completedAt(),
        job.failedAt(),
        job.nextAttemptAt(),
        job.updatedAt(),
        lifecycleProjectionService.project(job));
  }

  private String fileName(OcrJob job) {
    Optional<DriveFileMetadata> metadata =
        driveFileRepository.findByIdForWorkspace(job.fileId(), job.workspaceId());
    return metadata
        .map(file -> encryptionService.decryptText(file.encryptedName(), file.nameIv()))
        .orElse("Unavailable file");
  }
}
