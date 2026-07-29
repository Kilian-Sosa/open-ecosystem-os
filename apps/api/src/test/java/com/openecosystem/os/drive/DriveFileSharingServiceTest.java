package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.AuthorizationDecisionCode;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DriveFileSharingServiceTest {

  private static final String WORKSPACE_ID = "wrk_sharing";
  private static final String FILE_ID = "file_sharing";
  private static final String OWNER_ID = "usr_owner";
  private static final String GRANTEE_ID = "usr_grantee";
  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @Mock private ResourceAuthorizationService authorizationService;
  @Mock private DriveFileRepository driveFileRepository;
  @Mock private DriveFileUserGrantRepository grantRepository;
  @Mock private JdbcAuditRecordRepository auditRecordRepository;
  @Mock private TransactionTemplate transactionTemplate;

  private DriveFileSharingService service;
  private AuthenticatedPrincipal owner;

  @BeforeEach
  void setUp() {
    service =
        new DriveFileSharingService(
            authorizationService,
            driveFileRepository,
            grantRepository,
            auditRecordRepository,
            transactionTemplate);
    owner = new AuthenticatedPrincipal(OWNER_ID, WORKSPACE_ID, Set.of("VIEWER"), true);
    stubTransactionExecution();
  }

  @Test
  void ownerCanChangePrivateFileToWorkspaceAndAuditsOnlyPolicyMetadata() {
    stubOwnerDecision();
    DriveFileMetadata before = file(DriveFileVisibility.PRIVATE);
    when(driveFileRepository.findByIdForWorkspace(FILE_ID, WORKSPACE_ID))
        .thenReturn(java.util.Optional.of(before));
    when(driveFileRepository.updateVisibility(
            eq(FILE_ID), eq(WORKSPACE_ID), eq(DriveFileVisibility.WORKSPACE), any(Instant.class)))
        .thenReturn(true);

    DriveFileMetadata changed =
        service.changeVisibility(owner, FILE_ID, DriveFileVisibility.WORKSPACE);

    assertThat(changed.visibility()).isEqualTo(DriveFileVisibility.WORKSPACE);
    ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
    verify(auditRecordRepository).save(auditCaptor.capture());
    AuditRecord audit = auditCaptor.getValue();
    assertThat(audit.action()).isEqualTo("drive.file.visibility_changed");
    assertThat(audit.resourceType()).isEqualTo("file");
    assertThat(audit.resourceId()).isEqualTo(FILE_ID);
    assertThat(audit.attributes())
        .containsEntry("oldVisibility", "private")
        .containsEntry("newVisibility", "workspace")
        .doesNotContainKeys("filename", "email", "storageKey", "extractedText", "invoiceTotal");
  }

  @Test
  void ownerCanChangeWorkspaceFileBackToPrivate() {
    stubOwnerDecision();
    DriveFileMetadata before = file(DriveFileVisibility.WORKSPACE);
    when(driveFileRepository.findByIdForWorkspace(FILE_ID, WORKSPACE_ID))
        .thenReturn(java.util.Optional.of(before));
    when(driveFileRepository.updateVisibility(
            eq(FILE_ID), eq(WORKSPACE_ID), eq(DriveFileVisibility.PRIVATE), any(Instant.class)))
        .thenReturn(true);

    DriveFileMetadata changed =
        service.changeVisibility(owner, FILE_ID, DriveFileVisibility.PRIVATE);

    assertThat(changed.visibility()).isEqualTo(DriveFileVisibility.PRIVATE);
    ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
    verify(auditRecordRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().attributes())
        .containsEntry("oldVisibility", "workspace")
        .containsEntry("newVisibility", "private");
  }

  @Test
  void ownerCanGrantRegrantAndRevokeView() {
    stubOwnerDecision();
    DriveFileUserGrant grant = grant(true);
    when(grantRepository.grantOrReactivateView(
            anyString(),
            eq(WORKSPACE_ID),
            eq(FILE_ID),
            eq(GRANTEE_ID),
            eq(OWNER_ID),
            any(Instant.class)))
        .thenReturn(grant);

    assertThat(service.grantView(owner, FILE_ID, GRANTEE_ID)).isEqualTo(grant);
    verify(auditRecordRepository).save(any(AuditRecord.class));

    when(grantRepository.find(WORKSPACE_ID, FILE_ID, GRANTEE_ID, "file:view"))
        .thenReturn(java.util.Optional.of(grant));
    when(grantRepository.revokeView(
            eq(WORKSPACE_ID), eq(FILE_ID), eq(GRANTEE_ID), eq(OWNER_ID), any(Instant.class)))
        .thenReturn(java.util.Optional.of(grant(false)));

    assertThat(service.revokeView(owner, FILE_ID, GRANTEE_ID).active()).isFalse();
    verify(auditRecordRepository, org.mockito.Mockito.times(2)).save(any(AuditRecord.class));
  }

  @Test
  void onlyAllowOwnerCanManageFilePolicy() {
    for (AuthorizationDecisionCode code :
        new AuthorizationDecisionCode[] {
          AuthorizationDecisionCode.DENY_NO_POLICY,
          AuthorizationDecisionCode.DENY_INACTIVE_MEMBERSHIP,
          AuthorizationDecisionCode.DENY_WORKSPACE_MISMATCH,
          AuthorizationDecisionCode.DENY_RESOURCE_NOT_FOUND
        }) {
      when(authorizationService.decide(
              any(), eq(WORKSPACE_ID), eq(ResourceType.FILE), eq(FILE_ID), eq(ResourceAction.VIEW)))
          .thenReturn(AuthorizationDecision.deny(code));

      assertThatThrownBy(() -> service.grantView(owner, FILE_ID, GRANTEE_ID))
          .isInstanceOf(RuntimeException.class);
      verify(grantRepository, never())
          .grantOrReactivateView(
              anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
    verifyNoInteractions(auditRecordRepository, driveFileRepository);
  }

  @Test
  void alreadyRevokedOrAbsentGrantIsANoopWithoutDuplicateAudit() {
    stubOwnerDecision();
    DriveFileUserGrant revoked = grant(false);
    when(grantRepository.find(WORKSPACE_ID, FILE_ID, GRANTEE_ID, "file:view"))
        .thenReturn(java.util.Optional.of(revoked));

    assertThat(service.revokeView(owner, FILE_ID, GRANTEE_ID)).isEqualTo(revoked);
    verify(grantRepository, never())
        .revokeView(anyString(), anyString(), anyString(), anyString(), any(Instant.class));
    verifyNoInteractions(auditRecordRepository, transactionTemplate);

    when(grantRepository.find(WORKSPACE_ID, FILE_ID, "usr_absent", "file:view"))
        .thenReturn(java.util.Optional.empty());
    assertThatThrownBy(() -> service.revokeView(owner, FILE_ID, "usr_absent"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void policyFailurePreventsAuditAndAuditFailurePropagatesForAtomicTransaction() {
    stubOwnerDecision();
    when(grantRepository.grantOrReactivateView(
            anyString(),
            eq(WORKSPACE_ID),
            eq(FILE_ID),
            eq(GRANTEE_ID),
            eq(OWNER_ID),
            any(Instant.class)))
        .thenThrow(new IllegalStateException("policy failure"));

    assertThatThrownBy(() -> service.grantView(owner, FILE_ID, GRANTEE_ID))
        .hasMessage("policy failure");
    verifyNoInteractions(auditRecordRepository);

    DriveFileUserGrant grant = grant(true);
    when(grantRepository.grantOrReactivateView(
            anyString(),
            eq(WORKSPACE_ID),
            eq(FILE_ID),
            eq(GRANTEE_ID),
            eq(OWNER_ID),
            any(Instant.class)))
        .thenReturn(grant);
    org.mockito.Mockito.doThrow(new IllegalStateException("audit failure"))
        .when(auditRecordRepository)
        .save(any(AuditRecord.class));

    assertThatThrownBy(() -> service.grantView(owner, FILE_ID, GRANTEE_ID))
        .hasMessage("audit failure");
  }

  private void stubOwnerDecision() {
    when(authorizationService.decide(
            owner, WORKSPACE_ID, ResourceType.FILE, FILE_ID, ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_OWNER));
  }

  @SuppressWarnings("unchecked")
  private void stubTransactionExecution() {
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  private DriveFileMetadata file(DriveFileVisibility visibility) {
    return new DriveFileMetadata(
        FILE_ID,
        WORKSPACE_ID,
        OWNER_ID,
        visibility,
        "encrypted-name",
        "application/pdf",
        10,
        "checksum",
        "workspaces/" + WORKSPACE_ID + "/drive/" + FILE_ID + "/original",
        "AES-256-GCM",
        "key",
        "content-iv",
        "name-iv",
        NOW,
        NOW);
  }

  private DriveFileUserGrant grant(boolean active) {
    return new DriveFileUserGrant(
        "grant_1",
        WORKSPACE_ID,
        FILE_ID,
        GRANTEE_ID,
        "file:view",
        OWNER_ID,
        NOW,
        active ? null : OWNER_ID,
        active ? null : NOW.plusSeconds(1),
        NOW,
        NOW);
  }
}
