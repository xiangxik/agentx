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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotService {

  private final ChatbotRepository chatbotRepository;
  private final ChatbotAppearanceRepository chatbotAppearanceRepository;
  private final ChatbotBehaviorRepository chatbotBehaviorRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public ChatbotService(
      ChatbotRepository chatbotRepository,
      ChatbotAppearanceRepository chatbotAppearanceRepository,
      ChatbotBehaviorRepository chatbotBehaviorRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.chatbotRepository = chatbotRepository;
    this.chatbotAppearanceRepository = chatbotAppearanceRepository;
    this.chatbotBehaviorRepository = chatbotBehaviorRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ChatbotSummary create(CurrentUser actor, CreateChatbotRequest request) {
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
    appearance.setConfigJson(toJson(Map.of("brandVisible", true, "launcherPosition", "right")));
    chatbotAppearanceRepository.save(appearance);

    ChatbotBehavior behavior = new ChatbotBehavior();
    behavior.setTenantId(saved.getTenantId());
    behavior.setChatbotId(saved.getId());
    behavior.setFallbackMessage("暂时没有找到匹配答案，我已经记录你的问题。请稍后再试或留下联系方式。");
    behavior.setConfigJson(
        toJson(Map.of("allowDirectModel", false, "allowFeedback", true, "allowHandoff", true)));
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
    return chatbotRepository.findByTenantId(tenantId).stream().map(this::toSummary).toList();
  }

  @Transactional(readOnly = true)
  public PublicChatbotSnapshot getPublicSnapshot(String publicCode) {
    Chatbot chatbot = chatbotRepository.findByPublicCode(publicCode).orElseThrow();
    ChatbotAppearance appearance =
        chatbotAppearanceRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    ChatbotBehavior behavior =
        chatbotBehaviorRepository.findByChatbotId(chatbot.getId()).orElseThrow();
    return new PublicChatbotSnapshot(
        chatbot.getId(),
        chatbot.getTenantId(),
        chatbot.getName(),
        chatbot.getLanguage(),
        chatbot.getStatus(),
        chatbot.getPublicCode(),
        appearance.getThemeColor(),
        appearance.getWelcomeMessage(),
        behavior.getFallbackMessage());
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

  private ChatbotSummary toSummary(
      Chatbot chatbot, ChatbotAppearance appearance, ChatbotBehavior behavior) {
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
        behavior.getFallbackMessage());
  }

  private String toJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize chatbot config", exception);
    }
  }

  public record CreateChatbotRequest(
      Long tenantId, String name, String description, String language, ChatbotStatus status) {}

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
      String fallbackMessage) {}

  public record PublicChatbotSnapshot(
      Long chatbotId,
      Long tenantId,
      String name,
      String language,
      ChatbotStatus status,
      String publicCode,
      String themeColor,
      String welcomeMessage,
      String fallbackMessage) {}
}
