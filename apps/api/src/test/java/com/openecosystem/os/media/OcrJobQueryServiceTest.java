package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.AuthorizationDecisionCode;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.DriveFileVisibility;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.invoice.InvoiceExtractionSummary;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OcrJobQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  @Test
  void authorizesTheSourceBeforeLoadingAnyProtectedDetail() {
    TestContext context = new TestContext();
    OcrJobSourceReference source =
        new OcrJobSourceReference("ocr_allowed", "file_allowed", "wrk_test");
    OcrJob job = job("ocr_allowed", "file_allowed");
    when(context.repository.findSourceReferenceByIdForWorkspace("ocr_allowed", "wrk_test"))
        .thenReturn(Optional.of(source));
    when(context.authorizationService.decide(
            context.principal, "wrk_test", ResourceType.FILE, "file_allowed", ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_USER_GRANT));
    when(context.driveFiles.findByIdForWorkspace("file_allowed", "wrk_test"))
        .thenReturn(Optional.of(file("file_allowed")));
    when(context.repository.findDetailByIdForWorkspace("ocr_allowed", "wrk_test"))
        .thenReturn(Optional.of(job));
    when(context.encryption.decryptText("encrypted-name", "name-iv")).thenReturn("Invoice.pdf");
    when(context.ocrResults.findByJobIdForWorkspace("ocr_allowed", "wrk_test"))
        .thenReturn(Optional.empty());
    when(context.extractions.findByOcrJobIdForWorkspace("ocr_allowed", "wrk_test"))
        .thenReturn(Optional.empty());

    OcrJobDetailResponse response = context.service.getJob("ocr_allowed");

    assertThat(response.extractedText()).isEqualTo("legacy extracted secret");
    verify(context.authorizationService)
        .decide(
            context.principal, "wrk_test", ResourceType.FILE, "file_allowed", ResourceAction.VIEW);
    verify(context.driveFiles).findByIdForWorkspace("file_allowed", "wrk_test");
    verify(context.repository).findDetailByIdForWorkspace("ocr_allowed", "wrk_test");
  }

  @Test
  void returnsOneNonEnumeratingNotFoundWithoutProtectedLoadsWhenSourceIsDenied() {
    TestContext context = new TestContext();
    OcrJobSourceReference source =
        new OcrJobSourceReference("ocr_private", "file_private", "wrk_test");
    when(context.repository.findSourceReferenceByIdForWorkspace("ocr_private", "wrk_test"))
        .thenReturn(Optional.of(source));
    when(context.authorizationService.decide(
            context.principal, "wrk_test", ResourceType.FILE, "file_private", ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_NO_POLICY));

    assertThatThrownBy(() -> context.service.getJob("ocr_private"))
        .isInstanceOf(ApiException.class)
        .hasMessage("OCR job was not found");

    verify(context.driveFiles, never()).findByIdForWorkspace(any(), any());
    verify(context.repository, never()).findDetailByIdForWorkspace(any(), any());
    verify(context.ocrResults, never()).findByJobIdForWorkspace(any(), any());
    verify(context.extractions, never()).findByOcrJobIdForWorkspace(any(), any());
    verify(context.lifecycle, never()).project(any());
    verify(context.encryption, never()).decryptText(any(), any());
  }

  @Test
  void stopsBeforeAuthorizationWhenTheOcrJobIsMissing() {
    TestContext context = new TestContext();
    when(context.repository.findSourceReferenceByIdForWorkspace("ocr_missing", "wrk_test"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> context.service.getJob("ocr_missing"))
        .isInstanceOf(ApiException.class)
        .hasMessage("OCR job was not found");

    verify(context.authorizationService, never()).decide(any(), any(), any(), any(), any());
    verify(context.driveFiles, never()).findByIdForWorkspace(any(), any());
    verify(context.repository, never()).findDetailByIdForWorkspace(any(), any());
  }

  @Test
  void batchFiltersListCandidatesBeforeSafeSummaryAndMetadataLoads() {
    TestContext context = new TestContext();
    OcrJobSourceReference allowed =
        new OcrJobSourceReference("ocr_allowed", "file_allowed", "wrk_test");
    OcrJobSourceReference denied =
        new OcrJobSourceReference("ocr_denied", "file_denied", "wrk_test");
    when(context.repository.listSourceReferencesByWorkspace("wrk_test"))
        .thenReturn(List.of(allowed, denied));
    when(context.authorizationService.allowedResourceIds(
            context.principal,
            "wrk_test",
            ResourceType.FILE,
            Set.of("file_allowed", "file_denied"),
            ResourceAction.VIEW))
        .thenReturn(Set.of("file_allowed"));
    when(context.repository.findSummariesByIdsForWorkspace(List.of("ocr_allowed"), "wrk_test"))
        .thenReturn(List.of(summary("ocr_allowed", "file_allowed")));
    when(context.driveFiles.listByIdsForWorkspace("wrk_test", Set.of("file_allowed")))
        .thenReturn(List.of(file("file_allowed")));
    when(context.encryption.decryptText("encrypted-name", "name-iv")).thenReturn("Allowed.pdf");
    when(context.ocrResults.findPresentJobIdsForWorkspace("wrk_test", List.of("ocr_allowed")))
        .thenReturn(Set.of("ocr_allowed"));
    when(context.extractions.findSummariesByOcrJobIdsForWorkspace(
            "wrk_test", List.of("ocr_allowed")))
        .thenReturn(Map.<String, InvoiceExtractionSummary>of());

    OcrJobListResponse response = context.service.listJobs();

    assertThat(response.jobs())
        .extracting(OcrJobSummaryResponse::jobId)
        .containsExactly("ocr_allowed");
    verify(context.repository).findSummariesByIdsForWorkspace(List.of("ocr_allowed"), "wrk_test");
    verify(context.driveFiles).listByIdsForWorkspace("wrk_test", Set.of("file_allowed"));
    verify(context.ocrResults).findPresentJobIdsForWorkspace("wrk_test", List.of("ocr_allowed"));
    verify(context.extractions)
        .findSummariesByOcrJobIdsForWorkspace("wrk_test", List.of("ocr_allowed"));
    verify(context.encryption, never()).decryptText("denied-name", "denied-iv");
  }

  private static OcrJob job(String jobId, String fileId) {
    return new OcrJob(
        jobId,
        fileId,
        "wrk_test",
        "usr_owner",
        "evt_test",
        "corr_test",
        "application/pdf",
        "private-storage-key",
        OcrJobStatus.COMPLETED,
        "tesseract",
        1,
        3,
        "legacy extracted secret",
        23,
        null,
        null,
        NOW,
        NOW,
        NOW,
        null,
        null,
        NOW,
        NOW);
  }

  private static OcrJobSummary summary(String jobId, String fileId) {
    return new OcrJobSummary(
        jobId,
        fileId,
        "wrk_test",
        "application/pdf",
        OcrJobStatus.COMPLETED,
        "tesseract",
        1,
        3,
        23,
        null,
        "corr_test",
        NOW,
        NOW,
        NOW,
        null,
        NOW);
  }

  private static DriveFileMetadata file(String fileId) {
    return new DriveFileMetadata(
        fileId,
        "wrk_test",
        "usr_owner",
        DriveFileVisibility.PRIVATE,
        "encrypted-name",
        "application/pdf",
        10,
        "checksum",
        "storage-key",
        "AES-256-GCM",
        "key-id",
        "content-iv",
        "name-iv",
        NOW,
        NOW);
  }

  private static final class TestContext {
    private final AuthenticationContext authentication = mock(AuthenticationContext.class);
    private final OcrJobRepository repository = mock(OcrJobRepository.class);
    private final DriveFileRepository driveFiles = mock(DriveFileRepository.class);
    private final FileEncryptionService encryption = mock(FileEncryptionService.class);
    private final OcrJobLifecycleProjectionService lifecycle =
        mock(OcrJobLifecycleProjectionService.class);
    private final JdbcOcrResultRepository ocrResults = mock(JdbcOcrResultRepository.class);
    private final JdbcInvoiceExtractionRepository extractions =
        mock(JdbcInvoiceExtractionRepository.class);
    private final ResourceAuthorizationService authorizationService =
        mock(ResourceAuthorizationService.class);
    private final AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal("usr_viewer", "wrk_test", Set.of(), true);
    private final OcrJobQueryService service =
        new OcrJobQueryService(
            authentication,
            repository,
            driveFiles,
            encryption,
            lifecycle,
            ocrResults,
            extractions,
            authorizationService);

    private TestContext() {
      when(authentication.currentPrincipal()).thenReturn(principal);
    }
  }
}
