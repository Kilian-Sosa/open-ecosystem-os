package com.openecosystem.os.drive;

import com.openecosystem.os.common.events.EventEnvelope;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.flows.OcrCompletedEventParser;
import com.openecosystem.os.flows.OcrCompletedWorkflowTriggerService;
import com.openecosystem.os.media.FileUploadedEvent;
import com.openecosystem.os.media.FileUploadedOcrJobService;
import com.openecosystem.os.media.OcrJobRepository;
import com.openecosystem.os.media.OcrJobStatus;
import com.openecosystem.os.media.OcrRequestedPayload;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveRestoreRecovery {
  private static final Set<String> OCR_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");
  private final JdbcTemplate jdbc;
  private final OcrJobRepository jobs;
  private final FileUploadedOcrJobService uploads;
  private final com.openecosystem.os.media.FileUploadedEventParser uploadedParser;
  private final OcrCompletedEventParser completedParser;
  private final OcrCompletedWorkflowTriggerService workflows;
  private final JdbcEventOutboxRepository outbox;
  private final Clock clock;

  public DriveRestoreRecovery(
      JdbcTemplate jdbc,
      OcrJobRepository jobs,
      FileUploadedOcrJobService uploads,
      com.openecosystem.os.media.FileUploadedEventParser uploadedParser,
      OcrCompletedEventParser completedParser,
      OcrCompletedWorkflowTriggerService workflows,
      JdbcEventOutboxRepository outbox,
      Clock clock) {
    this.jdbc = jdbc;
    this.jobs = jobs;
    this.uploads = uploads;
    this.uploadedParser = uploadedParser;
    this.completedParser = completedParser;
    this.workflows = workflows;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void resume(List<DriveFileMetadata> files, String restoreId) {
    for (var file : files) {
      if (!OCR_TYPES.contains(file.contentType())) continue;
      var jobIds =
          jdbc.queryForList(
              "select job_id from ocr_jobs where workspace_id=? and file_id=?",
              String.class,
              file.workspaceId(),
              file.fileId());
      var now = clock.instant();
      String recoveryKey = "drive:restore:" + restoreId + ":" + file.fileId();
      if (jobIds.isEmpty()) {
        String uploadKey = "drive:" + file.fileId() + ":uploaded:v1";
        var originals =
            jdbc.queryForList(
                "select envelope_json from event_outbox where workspace_id=? and idempotency_key=?",
                String.class,
                file.workspaceId(),
                uploadKey);
        FileUploadedEvent event;
        if (!originals.isEmpty()) event = uploadedParser.parse(originals.getFirst());
        else {
          String eventId = Ids.newId("evt");
          outbox.save(
              new EventEnvelope<>(
                  eventId,
                  "FileUploaded",
                  1,
                  now,
                  file.workspaceId(),
                  file.ownerId(),
                  recoveryKey,
                  null,
                  "drive",
                  uploadKey,
                  new FileUploadedPayload(
                      file.fileId(),
                      file.contentType(),
                      file.sizeBytes(),
                      file.checksumSha256(),
                      file.storageKey(),
                      file.encryptionAlgorithm(),
                      file.encryptionKeyId(),
                      file.contentIv(),
                      file.createdAt())));
          event =
              new FileUploadedEvent(
                  eventId,
                  1,
                  now,
                  file.workspaceId(),
                  file.ownerId(),
                  recoveryKey,
                  uploadKey,
                  file.fileId(),
                  file.contentType(),
                  file.sizeBytes(),
                  file.storageKey(),
                  file.createdAt());
        }
        jdbc.update(
            "delete from event_consumptions where consumer_name=? and idempotency_key=?",
            "media.file-uploaded.ocr-job",
            event.idempotencyKey());
        uploads.queueOcrJobIfEligible(event);
        continue;
      }
      var job =
          jobs.findDetailByIdForWorkspace(jobIds.getFirst(), file.workspaceId()).orElseThrow();
      if (job.status() == OcrJobStatus.PROCESSING) {
        if (!jobs.requeueInterruptedForRestore(
            job.jobId(), file.workspaceId(), job.processingStartedAt(), now))
          throw new IllegalStateException("OCR claim changed during restore");
        job = jobs.findDetailByIdForWorkspace(job.jobId(), file.workspaceId()).orElseThrow();
      }
      if (job.status() == OcrJobStatus.QUEUED) {
        outbox.save(
            new EventEnvelope<>(
                Ids.newId("evt"),
                "OcrRequested",
                1,
                now,
                file.workspaceId(),
                job.actorId(),
                job.correlationId(),
                job.sourceEventId(),
                "media",
                recoveryKey + ":ocr:v1",
                new OcrRequestedPayload(
                    job.jobId(),
                    file.fileId(),
                    file.contentType(),
                    file.storageKey(),
                    job.attemptCount(),
                    job.maxAttempts(),
                    now)));
      } else if (job.status() == OcrJobStatus.COMPLETED) {
        // Preserve the original event identity so previously executed workflows remain idempotent.
        var envelopes =
            jdbc.queryForList(
                "select envelope_json from event_outbox where workspace_id=? and idempotency_key=?",
                String.class,
                file.workspaceId(),
                "media:ocr:" + job.jobId() + ":completed:v1");
        for (String envelope : envelopes) {
          var event = completedParser.parse(envelope);
          jdbc.update(
              "delete from event_consumptions where consumer_name=? and idempotency_key=?",
              "flows.ocr-completed.workflow-trigger",
              event.idempotencyKey());
          workflows.trigger(event);
        }
      }
    }
  }
}
