package com.openecosystem.os.audit;

import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

  private final AuthenticationContext authenticationContext;
  private final JdbcAuditRecordRepository auditRecordRepository;

  public AuditQueryService(
      AuthenticationContext authenticationContext,
      JdbcAuditRecordRepository auditRecordRepository) {
    this.authenticationContext = authenticationContext;
    this.auditRecordRepository = auditRecordRepository;
  }

  public AuditRecordListResponse listRecords(String correlationId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return new AuditRecordListResponse(
        auditRecordRepository.listByWorkspace(principal.workspaceId(), correlationId).stream()
            .map(this::toResponse)
            .toList());
  }

  private AuditRecordResponse toResponse(AuditRecord record) {
    return new AuditRecordResponse(
        record.auditId(),
        record.action(),
        record.resourceType(),
        record.resourceId(),
        record.actorId(),
        record.correlationId(),
        record.outcome().name(),
        record.attributes(),
        record.occurredAt());
  }
}
