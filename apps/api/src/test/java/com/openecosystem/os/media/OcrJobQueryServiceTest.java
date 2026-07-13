package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.invoice.InvoiceExtractionStatus;
import com.openecosystem.os.invoice.InvoiceExtractionSummary;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OcrJobQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

  @Test
  void listsMultipleJobsWithTwoMetadataProjectionsAndNoContentLoaders() {
    AuthenticationContext authenticationContext = mock(AuthenticationContext.class);
    OcrJobRepository ocrJobRepository = mock(OcrJobRepository.class);
    DriveFileRepository driveFileRepository = mock(DriveFileRepository.class);
    FileEncryptionService encryptionService = mock(FileEncryptionService.class);
    OcrJobLifecycleProjectionService lifecycleProjectionService =
        mock(OcrJobLifecycleProjectionService.class);
    JdbcOcrResultRepository ocrResultRepository = mock(JdbcOcrResultRepository.class);
    JdbcInvoiceExtractionRepository invoiceExtractionRepository =
        mock(JdbcInvoiceExtractionRepository.class);
    OcrJobQueryService service =
        new OcrJobQueryService(
            authenticationContext,
            ocrJobRepository,
            driveFileRepository,
            encryptionService,
            lifecycleProjectionService,
            ocrResultRepository,
            invoiceExtractionRepository);

    OcrJob first = job("ocr_first", "file_first");
    OcrJob second = job("ocr_second", "file_second");
    when(authenticationContext.currentPrincipal())
        .thenReturn(new AuthenticatedPrincipal("usr_test", "wrk_test", Set.of(), true));
    when(ocrJobRepository.listByWorkspace("wrk_test")).thenReturn(List.of(first, second));
    when(driveFileRepository.findByIdForWorkspace("file_first", "wrk_test"))
        .thenReturn(Optional.of(file("file_first")));
    when(driveFileRepository.findByIdForWorkspace("file_second", "wrk_test"))
        .thenReturn(Optional.of(file("file_second")));
    when(encryptionService.decryptText("encrypted-name", "name-iv")).thenReturn("Invoice.pdf");
    when(ocrResultRepository.findPresentJobIdsForWorkspace(
            "wrk_test", List.of("ocr_first", "ocr_second")))
        .thenReturn(Set.of("ocr_first", "ocr_second"));
    when(invoiceExtractionRepository.findSummariesByOcrJobIdsForWorkspace(
            "wrk_test", List.of("ocr_first", "ocr_second")))
        .thenReturn(
            Map.of(
                "ocr_first",
                new InvoiceExtractionSummary("invx_first", InvoiceExtractionStatus.COMPLETED),
                "ocr_second",
                new InvoiceExtractionSummary(
                    "invx_second", InvoiceExtractionStatus.REVIEW_REQUIRED)));

    OcrJobListResponse response = service.listJobs();

    assertThat(response.jobs()).hasSize(2);
    verify(ocrResultRepository)
        .findPresentJobIdsForWorkspace(eq("wrk_test"), eq(List.of("ocr_first", "ocr_second")));
    verify(invoiceExtractionRepository)
        .findSummariesByOcrJobIdsForWorkspace(
            eq("wrk_test"), eq(List.of("ocr_first", "ocr_second")));
    verify(ocrResultRepository, never()).findByJobIdForWorkspace("ocr_first", "wrk_test");
    verify(ocrResultRepository, never()).findByJobIdForWorkspace("ocr_second", "wrk_test");
    verify(invoiceExtractionRepository, never())
        .findByOcrJobIdForWorkspace("ocr_first", "wrk_test");
    verify(invoiceExtractionRepository, never())
        .findByOcrJobIdForWorkspace("ocr_second", "wrk_test");
  }

  private OcrJob job(String jobId, String fileId) {
    return new OcrJob(
        jobId,
        fileId,
        "wrk_test",
        "usr_test",
        "evt_test",
        "corr_test",
        "application/pdf",
        "storage-key",
        OcrJobStatus.COMPLETED,
        "tesseract",
        1,
        3,
        "sensitive text",
        14,
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

  private DriveFileMetadata file(String fileId) {
    return new DriveFileMetadata(
        fileId,
        "wrk_test",
        "usr_test",
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
}
