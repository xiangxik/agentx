package com.agentx.backend.audit.application;

import com.agentx.backend.audit.domain.AuditLog;
import com.agentx.backend.audit.domain.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  public void record(
      Long tenantId,
      Long actorUserId,
      String actionType,
      String targetType,
      String targetId,
      String result,
      String riskLevel,
      Map<String, Object> context) {
    AuditLog auditLog = new AuditLog();
    auditLog.setTenantId(tenantId);
    auditLog.setActorUserId(actorUserId);
    auditLog.setActionType(actionType);
    auditLog.setTargetType(targetType);
    auditLog.setTargetId(targetId);
    auditLog.setResult(result);
    auditLog.setRiskLevel(riskLevel);
    auditLog.setContextJson(toJson(context));
    auditLogRepository.save(auditLog);
  }

  private String toJson(Map<String, Object> context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }
}
