package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.common.Ids;
import com.openecosystem.os.worker.common.events.AuditRecordRepository;
import com.openecosystem.os.worker.common.events.EventConsumptionRepository;
import com.openecosystem.os.worker.common.events.EventEnvelope;
import com.openecosystem.os.worker.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.worker.metrics.WorkerMetrics;
import java.time.Clock;
import java.time.Duration;
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
  private final OcrResultRepository ocrResultRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final EventConsumptionRepository eventConsumptionRepository;
  private final AuditRecordRepository auditRecordRepository;
  private final TransactionTemplate transactionTemplate;
  private final WorkerMetrics workerMetrics;

  public OcrJobProcessor(
      OcrProvider ocrProvider,
      WorkerOcrProperties properties,
      OcrJobRepository ocrJobRepository,
      OcrResultRepository ocrResultRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      EventConsumptionRepository eventConsumptionRepository,
      AuditRecordRepository auditRecordRepository,
      TransactionTemplate transactionTemplate,
      WorkerMetrics workerMetrics) {
    this.ocrProvider = ocrProvider;
    this.properties = properties;
    this.ocrJobRepository = ocrJobRepository;
    this.ocrResultRepository = ocrResultRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.eventConsumptionRepository = eventConsumptionRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.transactionTemplate = transactionTemplate;
    this.workerMetrics = workerMetrics;
  }

  public OcrProcessingResult process(OcrRequestedEvent event) {
    long startedAtNanos = System.nanoTime();
    OcrProcessingOutcome outcome = null;
    try {
      OcrProcessingResult result = processInternal(event);
      outcome = result.outcome();
      return result;
    } catch (RuntimeException exception) {
      workerMetrics.recordOcrJobError(Duration.ofNanos(System.nanoTime() - startedAtNanos));
      throw exception;
    } finally {
      if (outcome != null)
        workerMetrics.recordOcrJob(outcome, Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
  }

  private OcrProcessingResult processInternal(OcrRequestedEvent event) {
    if (event.version() != 1
        || eventConsumptionRepository.exists(CONSUMER_NAME, event.idempotencyKey()))
      return new OcrProcessingResult(OcrProcessingOutcome.NO_OP, event.jobId());

    OcrJob claimedJob = claimJob(event);
    if (claimedJob == null) {
      return ocrJobRepository.findById(event.jobId()).map(OcrJob::terminal).orElse(true)
          ? new OcrProcessingResult(OcrProcessingOutcome.NO_OP, event.jobId())
          : new OcrProcessingResult(OcrProcessingOutcome.RETRY, event.jobId());
    }

    try {
      OcrDocumentResult providerResult =
          ocrProvider.extract(
              claimedJob,
              OcrExecutionDeadline.start(
                  Clock.systemUTC(),
                  claimedJob.processingStartedAt(),
                  properties.documentTimeout()));
      completeJob(event, claimedJob, providerResult);
      return new OcrProcessingResult(OcrProcessingOutcome.COMPLETED, event.jobId());
    } catch (RuntimeException exception) {
      return handleProviderFailure(event, claimedJob, exception);
    }
  }

  private OcrJob claimJob(OcrRequestedEvent event) {
    return transactionTemplate.execute(
        status -> {
          Optional<OcrJob> existing = ocrJobRepository.findById(event.jobId());
          if (existing.isEmpty()) return null;
          if (!matches(existing.get(), event)) return null;
          if (existing.get().terminal()) {
            eventConsumptionRepository.save(
                CONSUMER_NAME, event.idempotencyKey(), event.eventId(), Instant.now());
            return null;
          }

          Instant now = Instant.now();
          Optional<OcrJob> claimed =
              ocrJobRepository.claimForProcessing(
                  event.jobId(),
                  ocrProvider.name(),
                  now,
                  now.minus(properties.staleProcessingTimeout()));
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

  private void completeJob(OcrRequestedEvent event, OcrJob job, OcrDocumentResult result) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          ocrResultRepository.save(Ids.newId("ocrres"), job, result, now);
          if (!ocrJobRepository.complete(
              job.jobId(), job.processingStartedAt(), result.documentText(), now)) {
            throw new OcrProviderException(
                "OCR_CLAIM_STALE", "OCR processing claim was no longer active");
          }
          eventOutboxRepository.save(
              ocrCompletedEnvelope(event, job, result.documentText().length(), now));
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
                  result.provider(),
                  "attemptCount",
                  Integer.toString(job.attemptCount())));
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
      boolean requeued =
          Boolean.TRUE.equals(
              transactionTemplate.execute(
                  status ->
                      ocrJobRepository.queueRetry(
                          job.jobId(),
                          job.processingStartedAt(),
                          errorCode,
                          errorMessage,
                          now.plus(properties.retryDelay()),
                          now)));
      if (!requeued) {
        return new OcrProcessingResult(OcrProcessingOutcome.RETRY, job.jobId());
      }
      return new OcrProcessingResult(OcrProcessingOutcome.RETRY, job.jobId());
    }

    boolean failed =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status -> {
                  if (!ocrJobRepository.fail(
                      job.jobId(), job.processingStartedAt(), errorCode, errorMessage, now)) {
                    return false;
                  }
                  eventOutboxRepository.save(
                      ocrFailedEnvelope(event, job, errorCode, errorMessage, now));
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
                  return true;
                }));
    if (!failed) {
      return new OcrProcessingResult(OcrProcessingOutcome.RETRY, job.jobId());
    }
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
    return exception instanceof OcrProviderException
        ? exception.getMessage()
        : "OCR processing failed";
  }

  private boolean matches(OcrJob job, OcrRequestedEvent event) {
    return job.workspaceId().equals(event.workspaceId())
        && job.fileId().equals(event.fileId())
        && job.contentType().equals(event.contentType())
        && job.storageKey().equals(event.storageKey());
  }
}
