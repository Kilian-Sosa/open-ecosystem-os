package com.openecosystem.os.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/records")
public class AuditController {

  private final AuditQueryService auditQueryService;

  public AuditController(AuditQueryService auditQueryService) {
    this.auditQueryService = auditQueryService;
  }

  @GetMapping
  public AuditRecordListResponse listRecords(
      @RequestParam(name = "correlationId", required = false) String correlationId) {
    return auditQueryService.listRecords(correlationId);
  }
}
