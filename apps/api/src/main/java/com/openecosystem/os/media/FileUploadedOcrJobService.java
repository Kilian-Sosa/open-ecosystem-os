package com.openecosystem.os.media;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.events.EventConsumptionRepository;
import com.openecosystem.os.common.events.EventEnvelope;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.common.ids.Ids;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileUploadedOcrJobService {

  private static final String CONSUMER_NAME = "media.file-uploaded.ocr-job";
  private static final String OCR_REQUESTED = "OcrRequested";
  private static final String RESOURCE_TYPE_OCR_JOB = "ocr_job";
  private static final String QUEUED_ACTION = "media.ocr.job.queued";
  private static final Set<String> OCR_CONTENT_TYPES =
      Set.of("application/pdf", "image/png", "image/jpeg");

  private final MediaOcrProperties properties;
  private final OcrJobRepository ocrJobRepository;
  private final JdbcAuditRecordRepository auditRecordRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final EventConsumptionRepository eventConsumptionRepository;
  private final TransactionTemplate transactionTemplate;

  public FileUploadedOcrJobService(
      MediaOcrProperties properties,
      OcrJobRepository ocrJobRepository,
      JdbcAuditRecordRepository auditRecordRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      EventConsumptionRepository eventConsumptionRepository,
      TransactionTemplate transactionTemplate) {
    this.properties = properties;
    this.ocrJobRepository = ocrJobRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.eventConsumptionRepository = eventConsumptionRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public void queueOcrJobIfEligible(FileUploadedEvent event) {
    transactionTemplate.executeWithoutResult(
        status -> {
          if (eventConsumptionRepository.exists(CONSUMER_NAME, event.idempotencyKey())) {
            return;
          }

          Instant now = Instant.now();
          if (!isOcrEligible(event.contentType())) {
            eventConsumptionRepository.save(
                CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
            return;
          }

          if (ocrJobRepository.findByFileId(event.fileId()).isEmpty()) {
            OcrJob job = queuedJob(event, now);
            ocrJobRepository.saveQueued(job);
            auditRecordRepository.save(queuedAuditRecord(job, event, now));
            eventOutboxRepository.save(ocrRequestedEnvelope(job, event, now));
          }

          eventConsumptionRepository.save(
              CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
        });
  }

  private boolean isOcrEligible(String contentType) {
    return OCR_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
  }

  private OcrJob queuedJob(FileUploadedEvent event, Instant now) {
    return new OcrJob(
        Ids.newId("ocr"),
        event.fileId(),
        event.workspaceId(),
        event.actorId(),
        event.eventId(),
        event.correlationId(),
        event.contentType(),
        event.storageKey(),
        OcrJobStatus.QUEUED,
        null,
        0,
        properties.maxAttempts(),
        null,
        null,
        null,
        null,
        now,
        null,
        null,
        null,
        now,
        now,
        now);
  }

  private AuditRecord queuedAuditRecord(OcrJob job, FileUploadedEvent event, Instant now) {
    return new AuditRecord(
        Ids.newId("aud"),
        QUEUED_ACTION,
        RESOURCE_TYPE_OCR_JOB,
        job.jobId(),
        job.workspaceId(),
        job.actorId(),
        job.correlationId(),
        now,
        AuditOutcome.SUCCESS,
        Map.of(
            "fileId",
            job.fileId(),
            "contentType",
            job.contentType(),
            "sourceEventId",
            event.eventId(),
            "maxAttempts",
            Integer.toString(job.maxAttempts())));
  }

  private EventEnvelope<OcrRequestedPayload> ocrRequestedEnvelope(
      OcrJob job, FileUploadedEvent event, Instant now) {
    OcrRequestedPayload payload =
        new OcrRequestedPayload(
            job.jobId(),
            job.fileId(),
            job.contentType(),
            job.storageKey(),
            job.attemptCount(),
            job.maxAttempts(),
            now);
    return new EventEnvelope<>(
        Ids.newId("evt"),
        OCR_REQUESTED,
        1,
        now,
        job.workspaceId(),
        job.actorId(),
        job.correlationId(),
        event.eventId(),
        MediaModule.NAME,
        "media:ocr:" + job.jobId() + ":requested:v1",
        payload);
  }
}
