package com.agentx.backend.plan.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.chatbot.domain.ChatbotStatus;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.conversation.domain.ConversationRepository;
import com.agentx.backend.conversation.domain.MessageRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSource;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.agentx.backend.knowledge.domain.KnowledgeSourceType;
import com.agentx.backend.plan.domain.Plan;
import com.agentx.backend.plan.domain.PlanRepository;
import com.agentx.backend.plan.domain.PlanStatus;
import com.agentx.backend.plan.domain.TenantQuota;
import com.agentx.backend.plan.domain.TenantQuotaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanService {

  private final PlanRepository planRepository;
  private final TenantQuotaRepository tenantQuotaRepository;
  private final ChatbotRepository chatbotRepository;
  private final KnowledgeSourceRepository knowledgeSourceRepository;
  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public PlanService(
      PlanRepository planRepository,
      TenantQuotaRepository tenantQuotaRepository,
      ChatbotRepository chatbotRepository,
      KnowledgeSourceRepository knowledgeSourceRepository,
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.planRepository = planRepository;
    this.tenantQuotaRepository = tenantQuotaRepository;
    this.chatbotRepository = chatbotRepository;
    this.knowledgeSourceRepository = knowledgeSourceRepository;
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
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
  public PlanSummary update(CurrentUser actor, Long planId, UpdatePlanRequest request) {
    Plan plan = planRepository.findById(planId).orElseThrow();
    plan.setName(request.name());
    plan.setLimitsJson(toJson(request.limits()));
    auditLogService.record(
        null,
        actor.userId(),
        "PLAN_UPDATED",
        "PLAN",
        String.valueOf(planId),
        "SUCCESS",
        "LOW",
        Map.of("name", plan.getName()));
    return new PlanSummary(
        plan.getId(), plan.getCode(), plan.getName(), plan.getStatus().name(), request.limits());
  }

  @Transactional
  public PlanSummary updateStatus(CurrentUser actor, Long planId, PlanStatus status) {
    Plan plan = planRepository.findById(planId).orElseThrow();
    plan.setStatus(status);
    auditLogService.record(
        null,
        actor.userId(),
        "PLAN_STATUS_UPDATED",
        "PLAN",
        String.valueOf(planId),
        "SUCCESS",
        "MEDIUM",
        Map.of("status", status.name()));
    return new PlanSummary(
        plan.getId(), plan.getCode(), plan.getName(), plan.getStatus().name(), fromJson(plan.getLimitsJson()));
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

  @Transactional(readOnly = true)
  public TenantQuotaOverview getTenantQuotaOverview(CurrentUser actor, Long tenantId) {
    Long effectiveTenantId = actor.isSuperAdmin() ? tenantId : actor.tenantId();

    if (effectiveTenantId == null) {
      throw new IllegalStateException("TENANT_ID_REQUIRED");
    }

    TenantQuota tenantQuota = tenantQuotaRepository.findByTenantId(effectiveTenantId).orElseThrow();
    Plan plan = planRepository.findById(tenantQuota.getPlanId()).orElseThrow();
    Map<String, Long> limits = fromJson(plan.getLimitsJson());
    Map<String, Long> overrides = fromJson(tenantQuota.getOverridesJson());
    Map<String, Long> effectiveLimits = new java.util.LinkedHashMap<>(limits);
    effectiveLimits.putAll(overrides);

    Map<String, Long> usage =
        Map.of(
        "chatbots", chatbotRepository.countByTenantIdAndStatusNot(effectiveTenantId, ChatbotStatus.DELETED),
        "files", knowledgeSourceRepository.countByTenantIdAndSourceTypeAndStatusNot(effectiveTenantId, KnowledgeSourceType.FILE, KnowledgeSourceStatus.DELETED),
        "storageMb", getKnowledgeStorageUsageMb(effectiveTenantId),
            "messages", messageRepository.countByTenantId(effectiveTenantId),
            "conversations", conversationRepository.countByTenantId(effectiveTenantId),
            "tokens", 0L);

    return new TenantQuotaOverview(
        effectiveTenantId,
        plan.getId(),
        plan.getCode(),
        plan.getName(),
        plan.getStatus().name(),
        limits,
        overrides,
        effectiveLimits,
        usage);
  }

  @Transactional(readOnly = true)
  public void ensureTenantWithinLimit(Long tenantId, String resourceKey, long requestedUnits) {
    TenantQuota tenantQuota = tenantQuotaRepository.findByTenantId(tenantId).orElse(null);

    if (tenantQuota == null) {
      return;
    }

    Plan plan = planRepository.findById(tenantQuota.getPlanId()).orElseThrow();
    Map<String, Long> limits = fromJson(plan.getLimitsJson());
    Map<String, Long> overrides = fromJson(tenantQuota.getOverridesJson());
    long effectiveLimit = overrides.getOrDefault(resourceKey, limits.getOrDefault(resourceKey, 0L));

    if (effectiveLimit <= 0) {
      throw new IllegalStateException(limitCode(resourceKey));
    }

    long currentUsage =
        switch (resourceKey) {
          case "chatbots" ->
              chatbotRepository.countByTenantIdAndStatusNot(
                  tenantId, ChatbotStatus.DELETED);
          case "files" ->
              knowledgeSourceRepository.countByTenantIdAndSourceTypeAndStatusNot(
                  tenantId, KnowledgeSourceType.FILE, KnowledgeSourceStatus.DELETED);
          case "storageMb" -> getKnowledgeStorageUsageMb(tenantId);
          case "messages" -> messageRepository.countByTenantId(tenantId);
          case "conversations" -> conversationRepository.countByTenantId(tenantId);
          default -> throw new IllegalArgumentException("Unsupported quota resource: " + resourceKey);
        };

    if (currentUsage + requestedUnits > effectiveLimit) {
      throw new IllegalStateException(limitCode(resourceKey));
    }
  }

  private long getKnowledgeStorageUsageMb(Long tenantId) {
    long totalBytes =
        knowledgeSourceRepository
            .findByTenantIdAndSourceTypeAndStatusNot(
                tenantId, KnowledgeSourceType.FILE, KnowledgeSourceStatus.DELETED)
            .stream()
            .map(KnowledgeSource::getMetadataJson)
            .map(this::fromJson)
            .mapToLong(metadata -> metadata.getOrDefault("fileSizeBytes", 0L))
            .sum();

    if (totalBytes == 0) {
      return 0;
    }

    return (long) Math.ceil((double) totalBytes / (1024D * 1024D));
  }

  private String limitCode(String resourceKey) {
    return switch (resourceKey) {
      case "chatbots" -> "CHATBOTS_LIMIT_REACHED";
      case "files" -> "FILES_LIMIT_REACHED";
      case "storageMb" -> "STORAGE_MB_LIMIT_REACHED";
      case "messages" -> "MESSAGES_LIMIT_REACHED";
      case "conversations" -> "CONVERSATIONS_LIMIT_REACHED";
      default -> resourceKey.toUpperCase() + "_LIMIT_REACHED";
    };
  }

  private String toJson(Map<String, Long> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize plan data", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Long> fromJson(String data) {
    try {
      Map<String, Object> rawData = objectMapper.readValue(data, Map.class);
      return rawData.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  Map.Entry::getKey, entry -> ((Number) entry.getValue()).longValue()));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to deserialize plan data", exception);
    }
  }

  public record CreatePlanRequest(String code, String name, Map<String, Long> limits) {}

  public record UpdatePlanRequest(String name, Map<String, Long> limits) {}

  public record AssignTenantQuotaRequest(Long tenantId, Long planId, Map<String, Long> overrides) {}

  public record PlanSummary(
      Long id, String code, String name, String status, Map<String, Long> limits) {}

  public record TenantQuotaSummary(Long tenantId, Long planId, Map<String, Long> overrides) {}

  public record TenantQuotaOverview(
      Long tenantId,
      Long planId,
      String planCode,
      String planName,
      String planStatus,
      Map<String, Long> limits,
      Map<String, Long> overrides,
      Map<String, Long> effectiveLimits,
      Map<String, Long> usage) {}
}
