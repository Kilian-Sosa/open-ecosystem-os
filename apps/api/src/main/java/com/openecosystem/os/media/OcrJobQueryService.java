package com.openecosystem.os.media;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.invoice.InvoiceExtraction;
import com.openecosystem.os.invoice.InvoiceExtractionField;
import com.openecosystem.os.invoice.InvoiceExtractionSummary;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final ResourceAuthorizationService resourceAuthorizationService;

  public OcrJobQueryService(
      AuthenticationContext authenticationContext,
      OcrJobRepository ocrJobRepository,
      DriveFileRepository driveFileRepository,
      FileEncryptionService encryptionService,
      OcrJobLifecycleProjectionService lifecycleProjectionService,
      JdbcOcrResultRepository ocrResultRepository,
      JdbcInvoiceExtractionRepository invoiceExtractionRepository,
      ResourceAuthorizationService resourceAuthorizationService) {
    this.authenticationContext = authenticationContext;
    this.ocrJobRepository = ocrJobRepository;
    this.driveFileRepository = driveFileRepository;
    this.encryptionService = encryptionService;
    this.lifecycleProjectionService = lifecycleProjectionService;
    this.ocrResultRepository = ocrResultRepository;
    this.invoiceExtractionRepository = invoiceExtractionRepository;
    this.resourceAuthorizationService = resourceAuthorizationService;
  }

  public OcrJobListResponse listJobs() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    String workspaceId = principal.workspaceId();
    List<OcrJobSourceReference> sourceReferences =
        ocrJobRepository.listSourceReferencesByWorkspace(workspaceId);
    Set<String> candidateFileIds =
        sourceReferences.stream()
            .map(OcrJobSourceReference::fileId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<String> allowedFileIds =
        resourceAuthorizationService.allowedResourceIds(
            principal, workspaceId, ResourceType.FILE, candidateFileIds, ResourceAction.VIEW);
    List<OcrJobSourceReference> allowedReferences =
        sourceReferences.stream()
            .filter(reference -> allowedFileIds.contains(reference.fileId()))
            .toList();
    List<String> jobIds = allowedReferences.stream().map(OcrJobSourceReference::jobId).toList();
    if (jobIds.isEmpty()) {
      return new OcrJobListResponse(List.of());
    }
    List<OcrJobSummary> jobs = ocrJobRepository.findSummariesByIdsForWorkspace(jobIds, workspaceId);
    Map<String, DriveFileMetadata> filesById =
        driveFileRepository.listByIdsForWorkspace(workspaceId, allowedFileIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    DriveFileMetadata::fileId,
                    file -> file,
                    (left, right) -> left,
                    LinkedHashMap::new));
    Set<String> ocrResultJobIds =
        ocrResultRepository.findPresentJobIdsForWorkspace(workspaceId, jobIds);
    Map<String, InvoiceExtractionSummary> extractionSummaries =
        invoiceExtractionRepository.findSummariesByOcrJobIdsForWorkspace(workspaceId, jobIds);
    return new OcrJobListResponse(
        jobs.stream()
            .filter(job -> filesById.containsKey(job.fileId()))
            .map(
                job ->
                    toSummaryResponse(
                        job, filesById.get(job.fileId()), ocrResultJobIds, extractionSummaries))
            .toList());
  }

  public OcrJobDetailResponse getJob(String jobId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    OcrJobSourceReference source =
        ocrJobRepository
            .findSourceReferenceByIdForWorkspace(jobId, principal.workspaceId())
            .orElseThrow(this::ocrNotFound);
    AuthorizationDecision decision =
        resourceAuthorizationService.decide(
            principal,
            source.workspaceId(),
            ResourceType.FILE,
            source.fileId(),
            ResourceAction.VIEW);
    if (!decision.allowed()) {
      throw ocrNotFound();
    }
    DriveFileMetadata file =
        driveFileRepository
            .findByIdForWorkspace(source.fileId(), source.workspaceId())
            .orElseThrow(this::ocrNotFound);
    OcrJob job =
        ocrJobRepository
            .findDetailByIdForWorkspace(source.jobId(), source.workspaceId())
            .orElseThrow(this::ocrNotFound);
    return toDetailResponse(job, file);
  }

  private OcrJobSummaryResponse toSummaryResponse(
      OcrJobSummary job,
      DriveFileMetadata file,
      Set<String> ocrResultJobIds,
      Map<String, InvoiceExtractionSummary> extractionSummaries) {
    InvoiceExtractionSummary extraction = extractionSummaries.get(job.jobId());
    return new OcrJobSummaryResponse(
        job.jobId(),
        job.fileId(),
        fileName(file),
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
        ocrResultJobIds.contains(job.jobId()),
        extraction != null,
        extraction == null ? null : extraction.status().value(),
        extraction != null && extraction.status().value().equals("review_required"));
  }

  private OcrJobDetailResponse toDetailResponse(OcrJob job, DriveFileMetadata file) {
    Optional<OcrDocumentResult> result =
        ocrResultRepository.findByJobIdForWorkspace(job.jobId(), job.workspaceId());
    Optional<InvoiceExtraction> extraction =
        invoiceExtractionRepository.findByOcrJobIdForWorkspace(job.jobId(), job.workspaceId());
    return new OcrJobDetailResponse(
        job.jobId(),
        job.fileId(),
        fileName(file),
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

  private ApiException ocrNotFound() {
    return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "OCR job was not found");
  }
}
