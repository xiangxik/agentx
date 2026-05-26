package com.agentx.backend.chatbot.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.Chatbot;
import com.agentx.backend.chatbot.domain.ChatbotAppearance;
import com.agentx.backend.chatbot.domain.ChatbotAppearanceRepository;
import com.agentx.backend.chatbot.domain.ChatbotBehavior;
import com.agentx.backend.chatbot.domain.ChatbotBehaviorRepository;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.chatbot.domain.ChatbotStatus;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.plan.application.PlanService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatbotService {

  private final ChatbotRepository chatbotRepository;
  private final ChatbotAppearanceRepository chatbotAppearanceRepository;
  private final ChatbotBehaviorRepository chatbotBehaviorRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;
  private final PlanService planService;

  public ChatbotService(
      ChatbotRepository chatbotRepository,
      ChatbotAppearanceRepository chatbotAppearanceRepository,
      ChatbotBehaviorRepository chatbotBehaviorRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper,
      PlanService planService) {
    this.chatbotRepository = chatbotRepository;
    this.chatbotAppearanceRepository = chatbotAppearanceRepository;
    this.chatbotBehaviorRepository = chatbotBehaviorRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
    this.planService = planService;
  }

  @Transactional
  public ChatbotSummary create(CurrentUser actor, CreateChatbotRequest request) {
    planService.ensureTenantWithinLimit(request.tenantId(), "chatbots", 1);

    Chatbot chatbot = new Chatbot();
    chatbot.setTenantId(request.tenantId());
    chatbot.setName(request.name());
    chatbot.setDescription(request.description());
    chatbot.setLanguage(request.language());
    chatbot.setStatus(request.status());
    chatbot.setPublicCode(UUID.randomUUID().toString());
    Chatbot saved = chatbotRepository.save(chatbot);

    ChatbotAppearance appearance = new ChatbotAppearance();
    appearance.setTenantId(saved.getTenantId());
    appearance.setChatbotId(saved.getId());
    appearance.setThemeColor("#2563eb");
    appearance.setWelcomeMessage("你好，我是你的专属客服机器人。请告诉我你的问题。");
    appearance.setConfigJson(
      toJson(Map.of("brandVisible", true, "launcherPosition", "right", "stylePreset", "executive")));
    chatbotAppearanceRepository.save(appearance);

    ChatbotBehavior behavior = new ChatbotBehavior();
    behavior.setTenantId(saved.getTenantId());
    behavior.setChatbotId(saved.getId());
    behavior.setFallbackMessage("暂时没有找到匹配答案，我已经记录你的问题。请稍后再试或留下联系方式。");
    behavior.setConfigJson(
      toJson(
        Map.of(
          "allowDirectModel", false,
          "allowFeedback", true,
          "allowHandoff", true,
          "providerCode", "",
          "modelCode", "",
          "embeddingProviderCode", "",
          "embeddingModelCode", "")));
    chatbotBehaviorRepository.save(behavior);

    auditLogService.record(
        saved.getTenantId(),
        actor.userId(),
        "CHATBOT_CREATED",
        "CHATBOT",
        String.valueOf(saved.getId()),
        "SUCCESS",
        "LOW",
        Map.of("name", saved.getName(), "publicCode", saved.getPublicCode()));

    return toSummary(saved, appearance, behavior);
  }

  @Transactional(readOnly = true)
  public List<ChatbotSummary> listByTenant(Long tenantId) {
    return chatbotRepository.findByTenantIdAndStatusNot(tenantId, ChatbotStatus.DELETED).stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public ChatbotDetail get(CurrentUser actor, Long chatbotId) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    ChatbotAppearance appearance = chatbotAppearanceRepository.findByChatbotId(chatbotId).orElseThrow();
    ChatbotBehavior behavior = chatbotBehaviorRepository.findByChatbotId(chatbotId).orElseThrow();
    Map<String, Object> appearanceConfig = fromJson(appearance.getConfigJson());
    Map<String, Object> behaviorConfig = fromJson(behavior.getConfigJson());

    return new ChatbotDetail(
        chatbot.getId(),
        chatbot.getTenantId(),
        chatbot.getName(),
        chatbot.getDescription(),
        chatbot.getLanguage(),
        chatbot.getStatus(),
        chatbot.getPublicCode(),
        appearance.getThemeColor(),
        appearance.getWelcomeMessage(),
        behavior.getFallbackMessage(),
        Boolean.TRUE.equals(appearanceConfig.get("brandVisible")),
        String.valueOf(appearanceConfig.getOrDefault("launcherPosition", "right")),
        String.valueOf(appearanceConfig.getOrDefault("stylePreset", "executive")),
        Boolean.TRUE.equals(behaviorConfig.get("allowDirectModel")),
        Boolean.TRUE.equals(behaviorConfig.get("allowFeedback")),
        Boolean.TRUE.equals(behaviorConfig.get("allowHandoff")),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("providerCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("modelCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingProviderCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingModelCode", ""))));
  }

  @Transactional
  public ChatbotSummary update(CurrentUser actor, Long chatbotId, UpdateChatbotRequest request) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    chatbot.setName(request.name());
    chatbot.setDescription(request.description());
    chatbot.setLanguage(request.language());
    chatbot.setStatus(request.status());
    auditLogService.record(
        chatbot.getTenantId(),
        actor.userId(),
        "CHATBOT_UPDATED",
        "CHATBOT",
        String.valueOf(chatbot.getId()),
        "SUCCESS",
        "LOW",
        Map.of("name", chatbot.getName()));
    return toSummary(chatbot);
  }

  @Transactional
  public ChatbotSummary updateStatus(CurrentUser actor, Long chatbotId, ChatbotStatus status) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    chatbot.setStatus(status);
    auditLogService.record(
        chatbot.getTenantId(),
        actor.userId(),
        "CHATBOT_STATUS_UPDATED",
        "CHATBOT",
        String.valueOf(chatbot.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("status", status.name()));
    return toSummary(chatbot);
  }

  @Transactional
  public ChatbotSummary delete(CurrentUser actor, Long chatbotId) {
    return updateStatus(actor, chatbotId, ChatbotStatus.DELETED);
  }

  @Transactional
  public ChatbotDetail copy(CurrentUser actor, Long chatbotId) {
    Chatbot source = loadChatbot(actor, chatbotId);
    planService.ensureTenantWithinLimit(source.getTenantId(), "chatbots", 1);
    ChatbotAppearance sourceAppearance =
        chatbotAppearanceRepository.findByChatbotId(chatbotId).orElseThrow();
    ChatbotBehavior sourceBehavior = chatbotBehaviorRepository.findByChatbotId(chatbotId).orElseThrow();

    Chatbot chatbot = new Chatbot();
    chatbot.setTenantId(source.getTenantId());
    chatbot.setName(source.getName() + " Copy");
    chatbot.setDescription(source.getDescription());
    chatbot.setLanguage(source.getLanguage());
    chatbot.setStatus(ChatbotStatus.DRAFT);
    chatbot.setPublicCode(UUID.randomUUID().toString());
    Chatbot saved = chatbotRepository.save(chatbot);

    ChatbotAppearance appearance = new ChatbotAppearance();
    appearance.setTenantId(saved.getTenantId());
    appearance.setChatbotId(saved.getId());
    appearance.setThemeColor(sourceAppearance.getThemeColor());
    appearance.setWelcomeMessage(sourceAppearance.getWelcomeMessage());
    appearance.setConfigJson(sourceAppearance.getConfigJson());
    chatbotAppearanceRepository.save(appearance);

    ChatbotBehavior behavior = new ChatbotBehavior();
    behavior.setTenantId(saved.getTenantId());
    behavior.setChatbotId(saved.getId());
    behavior.setFallbackMessage(sourceBehavior.getFallbackMessage());
    behavior.setConfigJson(sourceBehavior.getConfigJson());
    chatbotBehaviorRepository.save(behavior);

    auditLogService.record(
        saved.getTenantId(),
        actor.userId(),
        "CHATBOT_COPIED",
        "CHATBOT",
        String.valueOf(saved.getId()),
        "SUCCESS",
        "LOW",
        Map.of("sourceChatbotId", source.getId()));

    return get(actor, saved.getId());
  }

  @Transactional
  public ChatbotDetail updateAppearance(
      CurrentUser actor, Long chatbotId, UpdateAppearanceRequest request) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    ChatbotAppearance appearance = chatbotAppearanceRepository.findByChatbotId(chatbotId).orElseThrow();
    appearance.setThemeColor(request.themeColor());
    appearance.setWelcomeMessage(request.welcomeMessage());
    appearance.setConfigJson(
        toJson(
            Map.of(
                "brandVisible", request.brandVisible(),
          "launcherPosition", request.launcherPosition(),
          "stylePreset", request.stylePreset())));
    auditLogService.record(
        chatbot.getTenantId(),
        actor.userId(),
        "CHATBOT_APPEARANCE_UPDATED",
        "CHATBOT",
        String.valueOf(chatbotId),
        "SUCCESS",
        "LOW",
        Map.of("themeColor", request.themeColor()));
    return get(actor, chatbotId);
  }

  @Transactional
  public ChatbotDetail updateBehavior(CurrentUser actor, Long chatbotId, UpdateBehaviorRequest request) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    ChatbotBehavior behavior = chatbotBehaviorRepository.findByChatbotId(chatbotId).orElseThrow();
    behavior.setFallbackMessage(request.fallbackMessage());
    behavior.setConfigJson(
        toJson(
            Map.of(
                "allowDirectModel", request.allowDirectModel(),
                "allowFeedback", request.allowFeedback(),
                "allowHandoff", request.allowHandoff(),
                "providerCode", request.providerCode() == null ? "" : request.providerCode().trim(),
                "modelCode", request.modelCode() == null ? "" : request.modelCode().trim(),
                "embeddingProviderCode",
                request.embeddingProviderCode() == null ? "" : request.embeddingProviderCode().trim(),
                "embeddingModelCode",
                request.embeddingModelCode() == null ? "" : request.embeddingModelCode().trim())));
    auditLogService.record(
        chatbot.getTenantId(),
        actor.userId(),
        "CHATBOT_BEHAVIOR_UPDATED",
        "CHATBOT",
        String.valueOf(chatbotId),
        "SUCCESS",
        "LOW",
        Map.of("allowFeedback", request.allowFeedback()));
    return get(actor, chatbotId);
  }

  @Transactional(readOnly = true)
  public PublicChatbotSnapshot getPublicSnapshot(String publicCode) {
    Chatbot chatbot =
      chatbotRepository
        .findByPublicCode(publicCode)
        .orElseThrow(() -> new IllegalArgumentException("CHATBOT_NOT_FOUND"));
    ChatbotAppearance appearance =
        chatbotAppearanceRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    ChatbotBehavior behavior =
        chatbotBehaviorRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    Map<String, Object> appearanceConfig = fromJson(appearance.getConfigJson());
    Map<String, Object> behaviorConfig = fromJson(behavior.getConfigJson());
    return new PublicChatbotSnapshot(
        chatbot.getId(),
        chatbot.getTenantId(),
        chatbot.getName(),
        chatbot.getLanguage(),
        chatbot.getStatus(),
        chatbot.getPublicCode(),
        appearance.getThemeColor(),
        appearance.getWelcomeMessage(),
      Boolean.TRUE.equals(appearanceConfig.get("brandVisible")),
      String.valueOf(appearanceConfig.getOrDefault("stylePreset", "executive")),
        Boolean.TRUE.equals(behaviorConfig.get("allowDirectModel")),
        behavior.getFallbackMessage(),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("providerCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("modelCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingProviderCode", ""))),
        emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingModelCode", ""))));
  }

  @Transactional(readOnly = true)
  public PublicChatbotSnapshot requireActiveSnapshot(String publicCode) {
    PublicChatbotSnapshot snapshot = getPublicSnapshot(publicCode);
    if (snapshot.status() != ChatbotStatus.ACTIVE) {
      throw new IllegalStateException("CHATBOT_NOT_ACTIVE");
    }
    return snapshot;
  }

  private ChatbotSummary toSummary(Chatbot chatbot) {
    ChatbotAppearance appearance =
        chatbotAppearanceRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    ChatbotBehavior behavior =
        chatbotBehaviorRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    return toSummary(chatbot, appearance, behavior);
  }

  private Chatbot loadChatbot(CurrentUser actor, Long chatbotId) {
    if (actor.isSuperAdmin()) {
      return chatbotRepository.findById(chatbotId).orElseThrow();
    }

    return chatbotRepository.findByIdAndTenantId(chatbotId, actor.tenantId()).orElseThrow();
  }

  private ChatbotSummary toSummary(
      Chatbot chatbot, ChatbotAppearance appearance, ChatbotBehavior behavior) {
    Map<String, Object> behaviorConfig = fromJson(behavior.getConfigJson());
    return new ChatbotSummary(
        chatbot.getId(),
        chatbot.getTenantId(),
        chatbot.getName(),
        chatbot.getDescription(),
        chatbot.getLanguage(),
        chatbot.getStatus(),
        chatbot.getPublicCode(),
        appearance.getThemeColor(),
        appearance.getWelcomeMessage(),
          behavior.getFallbackMessage(),
          Boolean.TRUE.equals(behaviorConfig.get("allowDirectModel")),
          emptyToNull(String.valueOf(behaviorConfig.getOrDefault("providerCode", ""))),
          emptyToNull(String.valueOf(behaviorConfig.getOrDefault("modelCode", ""))),
          emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingProviderCode", ""))),
          emptyToNull(String.valueOf(behaviorConfig.getOrDefault("embeddingModelCode", ""))));
  }

  private String toJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize chatbot config", exception);
    }
  }

  private Map<String, Object> fromJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to deserialize chatbot config", exception);
    }
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CreateChatbotRequest(
      Long tenantId, String name, String description, String language, ChatbotStatus status) {}

    public record UpdateChatbotRequest(
      String name, String description, String language, ChatbotStatus status) {}

  public record UpdateAppearanceRequest(
      String themeColor,
      String welcomeMessage,
      boolean brandVisible,
      String launcherPosition,
      String stylePreset) {}

  public record UpdateBehaviorRequest(
      String fallbackMessage,
      boolean allowDirectModel,
      boolean allowFeedback,
      boolean allowHandoff,
      String providerCode,
      String modelCode,
      String embeddingProviderCode,
      String embeddingModelCode) {}

  public record ChatbotSummary(
      Long id,
      Long tenantId,
      String name,
      String description,
      String language,
      ChatbotStatus status,
      String publicCode,
      String themeColor,
      String welcomeMessage,
      String fallbackMessage,
      boolean allowDirectModel,
      String providerCode,
      String modelCode,
      String embeddingProviderCode,
      String embeddingModelCode) {}

  public record ChatbotDetail(
      Long id,
      Long tenantId,
      String name,
      String description,
      String language,
      ChatbotStatus status,
      String publicCode,
      String themeColor,
      String welcomeMessage,
      String fallbackMessage,
      boolean brandVisible,
      String launcherPosition,
      String stylePreset,
      boolean allowDirectModel,
      boolean allowFeedback,
      boolean allowHandoff,
      String providerCode,
      String modelCode,
      String embeddingProviderCode,
      String embeddingModelCode) {}

  public record PublicChatbotSnapshot(
      Long chatbotId,
      Long tenantId,
      String name,
      String language,
      ChatbotStatus status,
      String publicCode,
      String themeColor,
      String welcomeMessage,
      boolean brandVisible,
      String stylePreset,
      boolean allowDirectModel,
      String fallbackMessage,
      String providerCode,
      String modelCode,
      String embeddingProviderCode,
      String embeddingModelCode) {}
}
