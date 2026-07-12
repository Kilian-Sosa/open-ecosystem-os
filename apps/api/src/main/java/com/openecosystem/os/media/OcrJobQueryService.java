package com.openecosystem.os.media;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.invoice.InvoiceExtraction;
import com.openecosystem.os.invoice.InvoiceExtractionField;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
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
  private final JdbcOcrResultRepository ocrResultRepository;
  private final JdbcInvoiceExtractionRepository invoiceExtractionRepository;

  public OcrJobQueryService(
      AuthenticationContext authenticationContext,
      OcrJobRepository ocrJobRepository,
      DriveFileRepository driveFileRepository,
      FileEncryptionService encryptionService,
      OcrJobLifecycleProjectionService lifecycleProjectionService,
      JdbcOcrResultRepository ocrResultRepository,
      JdbcInvoiceExtractionRepository invoiceExtractionRepository) {
    this.authenticationContext = authenticationContext;
    this.ocrJobRepository = ocrJobRepository;
    this.driveFileRepository = driveFileRepository;
    this.encryptionService = encryptionService;
    this.lifecycleProjectionService = lifecycleProjectionService;
    this.ocrResultRepository = ocrResultRepository;
    this.invoiceExtractionRepository = invoiceExtractionRepository;
  }

  public OcrJobListResponse listJobs() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new OcrJobListResponse(
        ocrJobRepository.listByWorkspace(principal.workspaceId()).stream()
            .flatMap(job -> authorized(job).stream())
            .map(this::toSummaryResponse)
            .toList());
  }

  public OcrJobDetailResponse getJob(String jobId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return ocrJobRepository
        .findByIdForWorkspace(jobId, principal.workspaceId())
        .flatMap(this::authorized)
        .map(this::toDetailResponse)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "OCR job was not found"));
  }

  private Optional<AuthorizedOcrJob> authorized(OcrJob job) {
    return driveFileRepository
        .findByIdForWorkspace(job.fileId(), job.workspaceId())
        .map(file -> new AuthorizedOcrJob(job, file));
  }

  private OcrJobSummaryResponse toSummaryResponse(AuthorizedOcrJob authorizedJob) {
    OcrJob job = authorizedJob.job();
    Optional<InvoiceExtraction> extraction =
        invoiceExtractionRepository.findByOcrJobIdForWorkspace(job.jobId(), job.workspaceId());
    return new OcrJobSummaryResponse(
        job.jobId(),
        job.fileId(),
        fileName(authorizedJob.file()),
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
        job.updatedAt(),
        extraction.isPresent(),
        extraction.map(value -> value.status().value()).orElse(null),
        extraction.map(value -> value.status().value().equals("review_required")).orElse(false));
  }

  private OcrJobDetailResponse toDetailResponse(AuthorizedOcrJob authorizedJob) {
    OcrJob job = authorizedJob.job();
    Optional<OcrDocumentResult> result =
        ocrResultRepository.findByJobIdForWorkspace(job.jobId(), job.workspaceId());
    Optional<InvoiceExtraction> extraction =
        invoiceExtractionRepository.findByOcrJobIdForWorkspace(job.jobId(), job.workspaceId());
    return new OcrJobDetailResponse(
        job.jobId(),
        job.fileId(),
        fileName(authorizedJob.file()),
        job.contentType(),
        job.status().value(),
        result.map(OcrDocumentResult::provider).orElse(job.provider()),
        job.attemptCount(),
        job.maxAttempts(),
        result.map(OcrDocumentResult::documentText).orElse(job.extractedText()),
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
        lifecycleProjectionService.project(job),
        extraction.map(InvoiceExtraction::extractorName).orElse(null),
        extraction.map(InvoiceExtraction::extractorVersion).orElse(null),
        extraction.map(value -> value.status().value()).orElse(null),
        extraction.map(InvoiceExtraction::aggregateConfidence).orElse(null),
        extraction.map(this::warnings).orElseGet(java.util.List::of),
        extraction.map(this::fields).orElseGet(java.util.List::of));
  }

  private java.util.List<OcrExtractionWarningResponse> warnings(InvoiceExtraction extraction) {
    return extraction.warnings().stream()
        .map(warning -> new OcrExtractionWarningResponse(warning.code(), warning.message()))
        .toList();
  }

  private java.util.List<OcrExtractionFieldResponse> fields(InvoiceExtraction extraction) {
    return extraction.fields().stream().map(this::field).toList();
  }

  private OcrExtractionFieldResponse field(InvoiceExtractionField field) {
    return new OcrExtractionFieldResponse(
        field.fieldKey(),
        field.displayValue(),
        field.normalizedValue(),
        field.status().value(),
        field.confidence(),
        field.sourcePageNumber(),
        field.sourceBlockNumber(),
        field.sourceParagraphNumber(),
        field.sourceLineNumber(),
        field.sources().stream()
            .map(
                source ->
                    new OcrExtractionFieldSourceResponse(
                        source.ocrWordId(), source.sourceRole(), source.sourceOrder()))
            .toList());
  }

  private String fileName(DriveFileMetadata file) {
    return encryptionService.decryptText(file.encryptedName(), file.nameIv());
  }

  private record AuthorizedOcrJob(OcrJob job, DriveFileMetadata file) {}
}
