package com.agentx.backend.audit.api;

import com.agentx.backend.audit.application.AuditLogService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AuditLogAdminController {

  private final AuditLogService auditLogService;

  public AuditLogAdminController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  public List<AuditLogService.AuditLogSummary> list(
      @RequestParam(required = false) Long tenantId,
      @RequestParam(required = false) Long actorUserId,
      @RequestParam(required = false) String actionType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String riskLevel,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdTo) {
    return auditLogService.list(
        new AuditLogService.SearchAuditLogRequest(
            tenantId, actorUserId, actionType, result, riskLevel, createdFrom, createdTo));
  }

  @GetMapping("/{auditLogId}")
  public AuditLogService.AuditLogDetail get(@PathVariable Long auditLogId) {
    return auditLogService.get(auditLogId);
  }
}