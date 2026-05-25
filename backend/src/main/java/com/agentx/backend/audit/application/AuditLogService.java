package com.agentx.backend.audit.application;

import com.agentx.backend.audit.domain.AuditLog;
import com.agentx.backend.audit.domain.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional(readOnly = true)
  public List<AuditLogSummary> list(SearchAuditLogRequest request) {
    return auditLogRepository
        .findAll(
            (root, query, criteriaBuilder) -> {
              List<Predicate> predicates = new ArrayList<>();

              if (request.tenantId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), request.tenantId()));
              }
              if (request.actorUserId() != null) {
                predicates.add(
                    criteriaBuilder.equal(root.get("actorUserId"), request.actorUserId()));
              }
              if (request.actionType() != null && !request.actionType().isBlank()) {
                predicates.add(
                    criteriaBuilder.equal(root.get("actionType"), request.actionType()));
              }
              if (request.result() != null && !request.result().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("result"), request.result()));
              }
              if (request.riskLevel() != null && !request.riskLevel().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("riskLevel"), request.riskLevel()));
              }
              if (request.createdFrom() != null) {
                predicates.add(
                    criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"), request.createdFrom()));
              }
              if (request.createdTo() != null) {
                predicates.add(
                    criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
              }

              query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
              return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
            })
        .stream()
        .map(
            auditLog ->
                new AuditLogSummary(
                    auditLog.getId(),
                    auditLog.getTenantId(),
                    auditLog.getActorUserId(),
                    auditLog.getActionType(),
                    auditLog.getTargetType(),
                    auditLog.getTargetId(),
                    auditLog.getResult(),
                    auditLog.getRiskLevel(),
                    auditLog.getCreatedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public AuditLogDetail get(Long auditLogId) {
    AuditLog auditLog = auditLogRepository.findById(auditLogId).orElseThrow();
    return new AuditLogDetail(
        auditLog.getId(),
        auditLog.getTenantId(),
        auditLog.getActorUserId(),
        auditLog.getActionType(),
        auditLog.getTargetType(),
        auditLog.getTargetId(),
        auditLog.getResult(),
        auditLog.getRiskLevel(),
        auditLog.getCreatedAt(),
        fromJson(auditLog.getContextJson()));
  }

  private String toJson(Map<String, Object> context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }

  private Map<String, Object> fromJson(String contextJson) {
    try {
      return objectMapper.readValue(contextJson == null ? "{}" : contextJson, new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      return Map.of();
    }
  }

  public record SearchAuditLogRequest(
      Long tenantId,
      Long actorUserId,
      String actionType,
      String result,
      String riskLevel,
      Instant createdFrom,
      Instant createdTo) {}

  public record AuditLogSummary(
      Long id,
      Long tenantId,
      Long actorUserId,
      String actionType,
      String targetType,
      String targetId,
      String result,
      String riskLevel,
      Instant createdAt) {}

  public record AuditLogDetail(
      Long id,
      Long tenantId,
      Long actorUserId,
      String actionType,
      String targetType,
      String targetId,
      String result,
      String riskLevel,
      Instant createdAt,
      Map<String, Object> context) {}
}
