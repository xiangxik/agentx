package com.agentx.backend.plan.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.plan.domain.Plan;
import com.agentx.backend.plan.domain.PlanRepository;
import com.agentx.backend.plan.domain.PlanStatus;
import com.agentx.backend.plan.domain.TenantQuota;
import com.agentx.backend.plan.domain.TenantQuotaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

  private final PlanRepository planRepository;
  private final TenantQuotaRepository tenantQuotaRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public PlanService(
      PlanRepository planRepository,
      TenantQuotaRepository tenantQuotaRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.planRepository = planRepository;
    this.tenantQuotaRepository = tenantQuotaRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PlanSummary create(CurrentUser actor, CreatePlanRequest request) {
    Plan plan = new Plan();
    plan.setCode(request.code());
    plan.setName(request.name());
    plan.setStatus(PlanStatus.ACTIVE);
    plan.setLimitsJson(toJson(request.limits()));
    Plan savedPlan = planRepository.save(plan);
    auditLogService.record(
        null,
        actor.userId(),
        "PLAN_CREATED",
        "PLAN",
        String.valueOf(savedPlan.getId()),
        "SUCCESS",
        "LOW",
        Map.of("code", savedPlan.getCode()));
    return new PlanSummary(
        savedPlan.getId(),
        savedPlan.getCode(),
        savedPlan.getName(),
        savedPlan.getStatus().name(),
        request.limits());
  }

  @Transactional(readOnly = true)
  public List<PlanSummary> list() {
    return planRepository.findAll().stream()
        .map(
            plan ->
                new PlanSummary(
                    plan.getId(),
                    plan.getCode(),
                    plan.getName(),
                    plan.getStatus().name(),
                    fromJson(plan.getLimitsJson())))
        .toList();
  }

  @Transactional
  public TenantQuotaSummary assign(CurrentUser actor, AssignTenantQuotaRequest request) {
    TenantQuota tenantQuota =
        tenantQuotaRepository.findByTenantId(request.tenantId()).orElseGet(TenantQuota::new);
    tenantQuota.setTenantId(request.tenantId());
    tenantQuota.setPlanId(request.planId());
    tenantQuota.setOverridesJson(toJson(request.overrides()));
    TenantQuota savedTenantQuota = tenantQuotaRepository.save(tenantQuota);
    auditLogService.record(
        request.tenantId(),
        actor.userId(),
        "TENANT_PLAN_ASSIGNED",
        "TENANT_QUOTA",
        String.valueOf(savedTenantQuota.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("planId", request.planId()));
    return new TenantQuotaSummary(
        savedTenantQuota.getTenantId(), savedTenantQuota.getPlanId(), request.overrides());
  }

  @Transactional(readOnly = true)
  public TenantQuotaSummary getTenantQuota(Long tenantId) {
    TenantQuota tenantQuota = tenantQuotaRepository.findByTenantId(tenantId).orElseThrow();
    return new TenantQuotaSummary(
        tenantQuota.getTenantId(),
        tenantQuota.getPlanId(),
        fromJson(tenantQuota.getOverridesJson()));
  }

  private String toJson(Map<String, Long> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize plan data", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Long> fromJson(String data) {
    try {
      return objectMapper.readValue(data, Map.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize plan data", exception);
    }
  }

  public record CreatePlanRequest(String code, String name, Map<String, Long> limits) {}

  public record AssignTenantQuotaRequest(Long tenantId, Long planId, Map<String, Long> overrides) {}

  public record PlanSummary(
      Long id, String code, String name, String status, Map<String, Long> limits) {}

  public record TenantQuotaSummary(Long tenantId, Long planId, Map<String, Long> overrides) {}
}
