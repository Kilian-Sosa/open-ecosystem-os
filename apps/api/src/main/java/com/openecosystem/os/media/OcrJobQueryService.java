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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    boolean ocrResultPresent =
        ocrResultRepository.findByJobIdForWorkspace(job.jobId(), job.workspaceId()).isPresent();
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
        ocrResultPresent,
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
        result.map(this::ocrResult).orElse(null),
        extraction.map(value -> extraction(value, wordsById(result))).orElse(null));
  }

  private OcrResultResponse ocrResult(OcrDocumentResult result) {
    return new OcrResultResponse(
        result.ocrResultId(),
        result.provider(),
        result.providerVersion(),
        result.pageCount(),
        result.wordCount());
  }

  private OcrExtractionResponse extraction(
      InvoiceExtraction extraction, Map<String, OcrWord> wordsById) {
    List<OcrExtractionWarningResponse> warnings = warnings(extraction);
    List<OcrExtractionFieldResponse> fields = fields(extraction, wordsById);
    return new OcrExtractionResponse(
        extraction.extractionId(),
        extraction.extractorName(),
        extraction.extractorVersion(),
        extraction.status().value(),
        extraction.status().value().equals("review_required"),
        extraction.aggregateConfidence(),
        fields.size(),
        warnings.size(),
        warnings,
        fields);
  }

  private List<OcrExtractionWarningResponse> warnings(InvoiceExtraction extraction) {
    return extraction.warnings().stream()
        .map(warning -> new OcrExtractionWarningResponse(warning.code(), null, warning.message()))
        .toList();
  }

  private List<OcrExtractionFieldResponse> fields(
      InvoiceExtraction extraction, Map<String, OcrWord> wordsById) {
    return extraction.fields().stream().map(field -> field(field, wordsById)).toList();
  }

  private OcrExtractionFieldResponse field(
      InvoiceExtractionField field, Map<String, OcrWord> wordsById) {
    return new OcrExtractionFieldResponse(
        field.fieldKey(),
        label(field.fieldKey()),
        field.displayValue(),
        field.normalizedValue(),
        field.status().value(),
        field.confidence(),
        field.sources().stream()
            .map(source -> provenance(source.sourceRole(), wordsById.get(source.ocrWordId())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList());
  }

  private Optional<OcrExtractionProvenanceResponse> provenance(String sourceRole, OcrWord word) {
    if (word == null) {
      return Optional.empty();
    }
    return Optional.of(
        new OcrExtractionProvenanceResponse(
            sourceRole,
            word.ocrWordId(),
            word.pageNumber(),
            word.blockNumber(),
            word.paragraphNumber(),
            word.lineNumber(),
            word.wordNumber(),
            word.readingOrder(),
            word.sourceKind()));
  }

  private Map<String, OcrWord> wordsById(Optional<OcrDocumentResult> result) {
    Map<String, OcrWord> wordsById = new LinkedHashMap<>();
    result.ifPresent(
        document ->
            document
                .pages()
                .forEach(
                    page -> page.words().forEach(word -> wordsById.put(word.ocrWordId(), word))));
    return wordsById;
  }

  private String label(String fieldKey) {
    return java.util.Arrays.stream(fieldKey.split("_"))
        .filter(segment -> !segment.isBlank())
        .map(segment -> Character.toUpperCase(segment.charAt(0)) + segment.substring(1))
        .reduce((left, right) -> left + " " + right)
        .orElse(fieldKey);
  }

  private String fileName(DriveFileMetadata file) {
    return encryptionService.decryptText(file.encryptedName(), file.nameIv());
  }

  private record AuthorizedOcrJob(OcrJob job, DriveFileMetadata file) {}
}
