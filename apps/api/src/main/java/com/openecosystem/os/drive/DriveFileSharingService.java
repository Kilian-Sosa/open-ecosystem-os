package com.openecosystem.os.drive;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.AuthorizationDecisionCode;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class DriveFileSharingService {

  private static final String FILE_VIEW_ACTION = "file:view";
  private static final String RESOURCE_TYPE_FILE = "file";

  private final ResourceAuthorizationService authorizationService;
  private final DriveFileRepository driveFileRepository;
  private final DriveFileUserGrantRepository grantRepository;
  private final JdbcAuditRecordRepository auditRecordRepository;
  private final TransactionTemplate transactionTemplate;

  public DriveFileSharingService(
      ResourceAuthorizationService authorizationService,
      DriveFileRepository driveFileRepository,
      DriveFileUserGrantRepository grantRepository,
      JdbcAuditRecordRepository auditRecordRepository,
      TransactionTemplate transactionTemplate) {
    this.authorizationService = authorizationService;
    this.driveFileRepository = driveFileRepository;
    this.grantRepository = grantRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public DriveFileUserGrant grantView(
      AuthenticatedPrincipal principal, String fileId, String granteeUserId) {
    requireOwner(principal, fileId);
    String workspaceId = principal.workspaceId();
    AtomicReference<DriveFileUserGrant> result = new AtomicReference<>();
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          DriveFileUserGrant grant =
              grantRepository.grantOrReactivateView(
                  Ids.newId("grant"), workspaceId, fileId, granteeUserId, principal.actorId(), now);
          auditRecordRepository.save(
              auditRecord(
                  "drive.file.view_granted",
                  fileId,
                  workspaceId,
                  principal.actorId(),
                  now,
                  Map.of(
                      "grantId", grant.grantId(),
                      "granteeUserId", grant.granteeUserId(),
                      "action", FILE_VIEW_ACTION)));
          result.set(grant);
        });
    return result.get();
  }

  public DriveFileUserGrant revokeView(
      AuthenticatedPrincipal principal, String fileId, String granteeUserId) {
    requireOwner(principal, fileId);
    String workspaceId = principal.workspaceId();
    DriveFileUserGrant existing =
        grantRepository
            .find(workspaceId, fileId, granteeUserId, FILE_VIEW_ACTION)
            .orElseThrow(this::fileNotFound);
    if (!existing.active()) {
      return existing;
    }

    AtomicReference<DriveFileUserGrant> result = new AtomicReference<>();
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          DriveFileUserGrant revoked =
              grantRepository
                  .revokeView(workspaceId, fileId, granteeUserId, principal.actorId(), now)
                  .orElseThrow(this::fileNotFound);
          auditRecordRepository.save(
              auditRecord(
                  "drive.file.view_revoked",
                  fileId,
                  workspaceId,
                  principal.actorId(),
                  now,
                  Map.of(
                      "grantId", revoked.grantId(),
                      "granteeUserId", revoked.granteeUserId(),
                      "action", FILE_VIEW_ACTION)));
          result.set(revoked);
        });
    return result.get();
  }

  public DriveFileMetadata changeVisibility(
      AuthenticatedPrincipal principal, String fileId, DriveFileVisibility visibility) {
    requireOwner(principal, fileId);
    if (visibility == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Drive file visibility is required");
    }

    String workspaceId = principal.workspaceId();
    DriveFileMetadata before =
        driveFileRepository
            .findByIdForWorkspace(fileId, workspaceId)
            .orElseThrow(this::fileNotFound);
    if (before.visibility() == visibility) {
      return before;
    }

    AtomicReference<DriveFileMetadata> result = new AtomicReference<>();
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          if (!driveFileRepository.updateVisibility(fileId, workspaceId, visibility, now)) {
            throw fileNotFound();
          }
          DriveFileMetadata changed = withVisibility(before, visibility, now);
          auditRecordRepository.save(
              auditRecord(
                  "drive.file.visibility_changed",
                  fileId,
                  workspaceId,
                  principal.actorId(),
                  now,
                  Map.of(
                      "oldVisibility", before.visibility().value(),
                      "newVisibility", visibility.value())));
          result.set(changed);
        });
    return result.get();
  }

  private void requireOwner(AuthenticatedPrincipal principal, String fileId) {
    String workspaceId = principal == null ? null : principal.workspaceId();
    AuthorizationDecision decision =
        authorizationService.decide(
            principal, workspaceId, ResourceType.FILE, fileId, ResourceAction.VIEW);
    if (!decision.allowed() || decision.code() != AuthorizationDecisionCode.ALLOW_OWNER) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          ApiErrorCode.FORBIDDEN,
          "Only the active Drive file owner may change file visibility or sharing");
    }
  }

  private AuditRecord auditRecord(
      String action,
      String fileId,
      String workspaceId,
      String actorId,
      Instant occurredAt,
      Map<String, String> attributes) {
    return new AuditRecord(
        Ids.newId("aud"),
        action,
        RESOURCE_TYPE_FILE,
        fileId,
        workspaceId,
        actorId,
        CorrelationContext.currentOrCreate(),
        occurredAt,
        AuditOutcome.SUCCESS,
        attributes);
  }

  private DriveFileMetadata withVisibility(
      DriveFileMetadata metadata, DriveFileVisibility visibility, Instant updatedAt) {
    return new DriveFileMetadata(
        metadata.fileId(),
        metadata.workspaceId(),
        metadata.ownerId(),
        visibility,
        metadata.encryptedName(),
        metadata.contentType(),
        metadata.sizeBytes(),
        metadata.checksumSha256(),
        metadata.storageKey(),
        metadata.encryptionAlgorithm(),
        metadata.encryptionKeyId(),
        metadata.contentIv(),
        metadata.nameIv(),
        metadata.createdAt(),
        updatedAt);
  }

  private ApiException fileNotFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive file was not found");
  }
}
