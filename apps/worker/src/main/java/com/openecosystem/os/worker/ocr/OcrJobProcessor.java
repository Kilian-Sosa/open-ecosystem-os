package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.common.Ids;
import com.openecosystem.os.worker.common.events.AuditRecordRepository;
import com.openecosystem.os.worker.common.events.EventConsumptionRepository;
import com.openecosystem.os.worker.common.events.EventEnvelope;
import com.openecosystem.os.worker.common.events.JdbcEventOutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OcrJobProcessor {

  private static final String CONSUMER_NAME = "worker.ocr-requested.processor";
  private static final String RESOURCE_TYPE_OCR_JOB = "ocr_job";
  private static final String OUTCOME_SUCCESS = "SUCCESS";
  private static final String OUTCOME_FAILURE = "FAILURE";
  private static final String OCR_STARTED = "OcrStarted";
  private static final String OCR_COMPLETED = "OcrCompleted";
  private static final String OCR_FAILED = "OcrFailed";

  private final OcrProvider ocrProvider;
  private final WorkerOcrProperties properties;
  private final OcrJobRepository ocrJobRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final EventConsumptionRepository eventConsumptionRepository;
  private final AuditRecordRepository auditRecordRepository;
  private final TransactionTemplate transactionTemplate;

  public OcrJobProcessor(
      OcrProvider ocrProvider,
      WorkerOcrProperties properties,
      OcrJobRepository ocrJobRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      EventConsumptionRepository eventConsumptionRepository,
      AuditRecordRepository auditRecordRepository,
      TransactionTemplate transactionTemplate) {
    this.ocrProvider = ocrProvider;
    this.properties = properties;
    this.ocrJobRepository = ocrJobRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.eventConsumptionRepository = eventConsumptionRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public OcrProcessingResult process(OcrRequestedEvent event) {
    if (event.version() != 1
        || eventConsumptionRepository.exists(CONSUMER_NAME, event.idempotencyKey())) {
      return new OcrProcessingResult(OcrProcessingOutcome.NO_OP, event.jobId());
    }

    OcrJob claimedJob = claimJob(event);
    if (claimedJob == null) {
      return new OcrProcessingResult(OcrProcessingOutcome.NO_OP, event.jobId());
    }

    try {
      OcrProviderResult providerResult = ocrProvider.extractText(claimedJob);
      completeJob(event, claimedJob, providerResult.extractedText());
      return new OcrProcessingResult(OcrProcessingOutcome.COMPLETED, event.jobId());
    } catch (RuntimeException exception) {
      return handleProviderFailure(event, claimedJob, exception);
    }
  }

  private OcrJob claimJob(OcrRequestedEvent event) {
    return transactionTemplate.execute(
        status -> {
          Optional<OcrJob> existing = ocrJobRepository.findById(event.jobId());
          if (existing.isEmpty()) {
            return null;
          }
          if (existing.get().terminal()) {
            eventConsumptionRepository.save(
                CONSUMER_NAME, event.idempotencyKey(), event.eventId(), Instant.now());
            return null;
          }

          Instant now = Instant.now();
          Optional<OcrJob> claimed =
              ocrJobRepository.claimForProcessing(event.jobId(), ocrProvider.name(), now);
          claimed.ifPresent(
              job -> {
                eventOutboxRepository.save(ocrStartedEnvelope(event, job, now));
                auditRecordRepository.save(
                    Ids.newId("aud"),
                    "media.ocr.job.started",
                    RESOURCE_TYPE_OCR_JOB,
                    job.jobId(),
                    job.workspaceId(),
                    job.actorId(),
                    job.correlationId(),
                    now,
                    OUTCOME_SUCCESS,
                    Map.of(
                        "fileId",
                        job.fileId(),
                        "provider",
                        ocrProvider.name(),
                        "attemptCount",
                        Integer.toString(job.attemptCount())));
              });
          return claimed.orElse(null);
        });
  }

  private void completeJob(OcrRequestedEvent event, OcrJob job, String extractedText) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          ocrJobRepository.complete(job.jobId(), extractedText, now);
          eventOutboxRepository.save(ocrCompletedEnvelope(event, job, extractedText.length(), now));
          auditRecordRepository.save(
              Ids.newId("aud"),
              "media.ocr.job.completed",
              RESOURCE_TYPE_OCR_JOB,
              job.jobId(),
              job.workspaceId(),
              job.actorId(),
              job.correlationId(),
              now,
              OUTCOME_SUCCESS,
              Map.of(
                  "fileId",
                  job.fileId(),
                  "provider",
                  ocrProvider.name(),
                  "attemptCount",
                  Integer.toString(job.attemptCount()),
                  "extractedTextLength",
                  Integer.toString(extractedText.length())));
          eventConsumptionRepository.save(
              CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
        });
  }

  private OcrProcessingResult handleProviderFailure(
      OcrRequestedEvent event, OcrJob job, RuntimeException exception) {
    String errorCode =
        exception instanceof OcrProviderException providerException
            ? providerException.code()
            : "OCR_PROVIDER_FAILED";
    String errorMessage = sanitizedMessage(exception);
    boolean finalAttempt = job.attemptCount() >= job.maxAttempts();
    Instant now = Instant.now();

    if (!finalAttempt) {
      ocrJobRepository.queueRetry(
          job.jobId(), errorCode, errorMessage, now.plus(properties.retryDelay()), now);
      return new OcrProcessingResult(OcrProcessingOutcome.RETRY, job.jobId());
    }

    transactionTemplate.executeWithoutResult(
        status -> {
          ocrJobRepository.fail(job.jobId(), errorCode, errorMessage, now);
          eventOutboxRepository.save(ocrFailedEnvelope(event, job, errorCode, errorMessage, now));
          auditRecordRepository.save(
              Ids.newId("aud"),
              "media.ocr.job.failed",
              RESOURCE_TYPE_OCR_JOB,
              job.jobId(),
              job.workspaceId(),
              job.actorId(),
              job.correlationId(),
              now,
              OUTCOME_FAILURE,
              Map.of(
                  "fileId",
                  job.fileId(),
                  "provider",
                  ocrProvider.name(),
                  "attemptCount",
                  Integer.toString(job.attemptCount()),
                  "errorCode",
                  errorCode));
          eventConsumptionRepository.save(
              CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
        });
    return new OcrProcessingResult(OcrProcessingOutcome.DEAD_LETTER, job.jobId());
  }

  private EventEnvelope<OcrStartedPayload> ocrStartedEnvelope(
      OcrRequestedEvent event, OcrJob job, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        OCR_STARTED,
        1,
        now,
        job.workspaceId(),
        job.actorId(),
        job.correlationId(),
        event.eventId(),
        "media",
        "media:ocr:" + job.jobId() + ":started:v1:attempt:" + job.attemptCount(),
        new OcrStartedPayload(
            job.jobId(),
            job.fileId(),
            ocrProvider.name(),
            job.attemptCount(),
            job.maxAttempts(),
            now));
  }

  private EventEnvelope<OcrCompletedPayload> ocrCompletedEnvelope(
      OcrRequestedEvent event, OcrJob job, int extractedTextLength, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        OCR_COMPLETED,
        1,
        now,
        job.workspaceId(),
        job.actorId(),
        job.correlationId(),
        event.eventId(),
        "media",
        "media:ocr:" + job.jobId() + ":completed:v1",
        new OcrCompletedPayload(
            job.jobId(),
            job.fileId(),
            ocrProvider.name(),
            job.attemptCount(),
            extractedTextLength,
            now));
  }

  private EventEnvelope<OcrFailedPayload> ocrFailedEnvelope(
      OcrRequestedEvent event, OcrJob job, String errorCode, String errorMessage, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        OCR_FAILED,
        1,
        now,
        job.workspaceId(),
        job.actorId(),
        job.correlationId(),
        event.eventId(),
        "media",
        "media:ocr:" + job.jobId() + ":failed:v1:attempt:" + job.attemptCount(),
        new OcrFailedPayload(
            job.jobId(),
            job.fileId(),
            ocrProvider.name(),
            job.attemptCount(),
            job.maxAttempts(),
            errorCode,
            errorMessage,
            now));
  }

  private String sanitizedMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return "OCR provider failed";
    }
    return message.length() > 256 ? message.substring(0, 256) : message;
  }
}
