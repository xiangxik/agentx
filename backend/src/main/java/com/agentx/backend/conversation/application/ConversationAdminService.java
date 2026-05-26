package com.agentx.backend.conversation.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.conversation.domain.Conversation;
import com.agentx.backend.conversation.domain.ConversationRepository;
import com.agentx.backend.conversation.domain.ConversationStatus;
import com.agentx.backend.conversation.domain.Message;
import com.agentx.backend.conversation.domain.MessageRepository;
import com.agentx.backend.model.domain.ModelCallLogRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConversationAdminService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final ChatbotRepository chatbotRepository;
  private final ModelCallLogRepository modelCallLogRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public ConversationAdminService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      ChatbotRepository chatbotRepository,
      ModelCallLogRepository modelCallLogRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.chatbotRepository = chatbotRepository;
    this.modelCallLogRepository = modelCallLogRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<ConversationSummary> list(CurrentUser actor, Long chatbotId, ConversationStatus status) {
    return conversationRepository.findByTenantIdOrderByIdDesc(actor.tenantId()).stream()
        .filter(conversation -> chatbotId == null || chatbotId.equals(conversation.getChatbotId()))
        .filter(conversation -> status == null || status == conversation.getStatus())
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public ConversationDetail get(CurrentUser actor, Long conversationId) {
    Conversation conversation = loadConversation(actor, conversationId);
    List<ConversationMessage> messages =
        messageRepository.findByConversationIdOrderByIdAsc(conversationId).stream()
            .map(this::toMessage)
            .toList();
    List<ModelCallSummary> modelCalls =
      modelCallLogRepository.findByConversationIdOrderByIdAsc(conversationId).stream()
        .map(this::toModelCall)
        .toList();
    return new ConversationDetail(
        conversation.getId(),
        conversation.getTenantId(),
        conversation.getChatbotId(),
        chatbotRepository.findById(conversation.getChatbotId()).map(chatbot -> chatbot.getName()).orElse("未知 Chatbot"),
        conversation.getAnonymousVisitorId(),
        conversation.getEntryType(),
        conversation.getStatus().name(),
        conversation.getCreatedAt().toString(),
        conversation.getUpdatedAt().toString(),
          toConversationMetadata(fromJson(conversation.getMetadataJson())),
          messages,
          modelCalls);
  }

  @Transactional
  public ConversationSummary updateStatus(CurrentUser actor, Long conversationId, ConversationStatus status) {
    Conversation conversation = loadConversation(actor, conversationId);
    conversation.setStatus(status);
    auditLogService.record(
        conversation.getTenantId(),
        actor.userId(),
        "CONVERSATION_STATUS_UPDATED",
        "CONVERSATION",
        String.valueOf(conversation.getId()),
        "SUCCESS",
        "LOW",
        Map.of("status", status.name()));
    return toSummary(conversation);
  }

  @Transactional(readOnly = true)
  public ConversationExport export(CurrentUser actor, Long conversationId) {
    ConversationDetail detail = get(actor, conversationId);
    ExportSummary summary = buildExportSummary(detail);

    try {
      String content =
          objectMapper.writeValueAsString(
          new LinkedHashMap<>(
            Map.of(
              "exportedAt", Instant.now().toString(),
              "summary", summary,
              "conversation", detail)));

      auditLogService.record(
          detail.tenantId(),
          actor.userId(),
          "CONVERSATION_EXPORTED",
          "CONVERSATION",
          String.valueOf(detail.id()),
          "SUCCESS",
          "MEDIUM",
          Map.of("messageCount", detail.messages().size()));

      return new ConversationExport(
          buildExportFileName(detail, summary), content);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize conversation export", exception);
    }
  }

  @Transactional
  public ConversationSummary delete(CurrentUser actor, Long conversationId) {
    Conversation conversation = loadConversation(actor, conversationId);
    conversation.setStatus(ConversationStatus.DELETED);
    auditLogService.record(
        conversation.getTenantId(),
        actor.userId(),
        "CONVERSATION_DELETED",
        "CONVERSATION",
        String.valueOf(conversation.getId()),
        "SUCCESS",
        "HIGH",
        Map.of("status", ConversationStatus.DELETED.name()));
    return toSummary(conversation);
  }

  private Conversation loadConversation(CurrentUser actor, Long conversationId) {
    return conversationRepository.findByIdAndTenantId(conversationId, actor.tenantId()).orElseThrow();
  }

  private ConversationSummary toSummary(Conversation conversation) {
    List<Message> messages = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
    String latestMessage = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getContent();

    return new ConversationSummary(
        conversation.getId(),
        conversation.getTenantId(),
        conversation.getChatbotId(),
        chatbotRepository.findById(conversation.getChatbotId()).map(chatbot -> chatbot.getName()).orElse("未知 Chatbot"),
        conversation.getAnonymousVisitorId(),
        conversation.getEntryType(),
        conversation.getStatus().name(),
        latestMessage,
        messages.size(),
        conversation.getCreatedAt().toString(),
        conversation.getUpdatedAt().toString());
  }

  private ConversationMessage toMessage(Message message) {
    Map<String, Object> metadata = fromJson(message.getMetadataJson());
    return new ConversationMessage(
        message.getId(),
        message.getRole().name(),
        message.getStatus().name(),
        message.getContent(),
        metadata,
        readString(metadata, "sourceType"),
        readString(metadata, "language"),
        readLong(metadata, "faqId"),
        readLong(metadata, "knowledgeSourceId"),
        readInteger(metadata, "knowledgeScore"),
        toCitations(metadata.get("citations")),
        toModelMetadata(metadata.get("model")),
        message.getCreatedAt().toString());
  }

  private ConversationMetadata toConversationMetadata(Map<String, Object> metadata) {
    return new ConversationMetadata(
        readString(metadata, "domain"),
        readString(metadata, "ipAddress") == null ? readString(metadata, "ip") : readString(metadata, "ipAddress"),
        readString(metadata, "userAgent"),
        readString(metadata, "chatbotPublicCode"),
        readString(metadata, "chatbotName"),
        metadata);
  }

  private List<CitationView> toCitations(Object value) {
    if (!(value instanceof List<?> items)) {
      return List.of();
    }
    return items.stream()
        .filter(Map.class::isInstance)
        .map(this::castMetadataMap)
        .map(
            item ->
                new CitationView(
                    readLong(item, "sourceId"),
                    readString(item, "title"),
                    readString(item, "sourceType"),
                    readString(item, "sourceLink")))
        .toList();
  }

  private ModelMetadataView toModelMetadata(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return null;
    }
    Map<String, Object> typed = castMetadataMap(map);
    return new ModelMetadataView(
        readLong(typed, "logId"),
        readString(typed, "provider"),
        readString(typed, "model"),
        readString(typed, "mode"),
        readInteger(typed, "promptTokens"),
        readInteger(typed, "completionTokens"),
        readInteger(typed, "totalTokens"),
        readDouble(typed, "estimatedCost"));
  }

  private ModelCallSummary toModelCall(com.agentx.backend.model.domain.ModelCallLog log) {
    return new ModelCallSummary(
        log.getId(),
        log.getProviderCode(),
        log.getModelCode(),
        log.getPurpose().name(),
        log.getStatus().name(),
        log.getPromptTokens(),
        log.getCompletionTokens(),
        log.getTotalTokens(),
        log.getEstimatedCost(),
        log.getLatencyMs(),
        log.getErrorMessage(),
        fromJson(log.getMetadataJson()),
        log.getCreatedAt().toString());
  }

  private ExportSummary buildExportSummary(ConversationDetail detail) {
    ModelCallSummary latestModelCall =
        detail.modelCalls().isEmpty() ? null : detail.modelCalls().get(detail.modelCalls().size() - 1);
    int totalTokens = detail.modelCalls().stream().mapToInt(ModelCallSummary::totalTokens).sum();
    double totalCost = detail.modelCalls().stream().mapToDouble(ModelCallSummary::estimatedCost).sum();
    long successCount =
        detail.modelCalls().stream().filter(call -> "SUCCESS".equals(call.status())).count();
    return new ExportSummary(
        detail.chatbotName(),
        detail.status(),
        detail.messages().size(),
        detail.modelCalls().size(),
        latestModelCall == null ? null : latestModelCall.provider(),
        latestModelCall == null ? null : latestModelCall.model(),
        totalTokens,
        totalCost,
        successCount);
  }

  private String buildExportFileName(ConversationDetail detail, ExportSummary summary) {
    StringBuilder fileName = new StringBuilder("conversation");
    fileName.append("-").append(detail.id());
    fileName.append("-").append(sanitizeFileToken(detail.chatbotName()));
    if (summary.latestProvider() != null) {
      fileName.append("-provider-").append(sanitizeFileToken(summary.latestProvider()));
    }
    if (summary.latestModel() != null) {
      fileName.append("-model-").append(sanitizeFileToken(summary.latestModel()));
    }
    fileName.append(".json");
    return fileName.toString();
  }

  private String sanitizeFileToken(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    sanitized = sanitized.replaceAll("-+", "-");
    sanitized = sanitized.replaceAll("^-|-$", "");
    return sanitized.isBlank() ? "unknown" : sanitized;
  }

  private String readString(Map<String, Object> value, String key) {
    Object item = value.get(key);
    if (item == null) {
      return null;
    }
    String text = String.valueOf(item);
    return text.isBlank() ? null : text;
  }

  private Long readLong(Map<String, Object> value, String key) {
    Object item = value.get(key);
    if (item == null) {
      return null;
    }
    if (item instanceof Number number) {
      return number.longValue();
    }
    String text = String.valueOf(item);
    return text.isBlank() ? null : Long.valueOf(text);
  }

  private Integer readInteger(Map<String, Object> value, String key) {
    Object item = value.get(key);
    if (item == null) {
      return null;
    }
    if (item instanceof Number number) {
      return number.intValue();
    }
    String text = String.valueOf(item);
    return text.isBlank() ? null : Integer.valueOf(text);
  }

  private Double readDouble(Map<String, Object> value, String key) {
    Object item = value.get(key);
    if (item == null) {
      return null;
    }
    if (item instanceof Number number) {
      return number.doubleValue();
    }
    String text = String.valueOf(item);
    return text.isBlank() ? null : Double.valueOf(text);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMetadataMap(Object value) {
    return (Map<String, Object>) value;
  }

  private Map<String, Object> fromJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to parse conversation payload", exception);
    }
  }

  public record ConversationSummary(
      Long id,
      Long tenantId,
      Long chatbotId,
      String chatbotName,
      String anonymousVisitorId,
      String entryType,
      String status,
      String latestMessage,
      int messageCount,
      String createdAt,
      String updatedAt) {}

  public record ConversationDetail(
      Long id,
      Long tenantId,
      Long chatbotId,
      String chatbotName,
      String anonymousVisitorId,
      String entryType,
      String status,
      String createdAt,
      String updatedAt,
        ConversationMetadata metadata,
        List<ConversationMessage> messages,
        List<ModelCallSummary> modelCalls) {}

      public record ConversationMetadata(
        String domain,
        String ipAddress,
        String userAgent,
        String chatbotPublicCode,
        String chatbotName,
        Map<String, Object> raw) {}

  public record ConversationMessage(
      Long id,
      String role,
      String status,
      String content,
      Map<String, Object> metadata,
        String sourceType,
        String language,
        Long faqId,
        Long knowledgeSourceId,
        Integer knowledgeScore,
        List<CitationView> citations,
        ModelMetadataView model,
      String createdAt) {}

      public record CitationView(Long sourceId, String title, String sourceType, String sourceLink) {}

      public record ModelMetadataView(
        Long logId,
        String provider,
        String model,
        String mode,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Double estimatedCost) {}

      public record ModelCallSummary(
        Long id,
        String provider,
        String model,
        String purpose,
        String status,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double estimatedCost,
        long latencyMs,
        String errorMessage,
        Map<String, Object> metadata,
        String createdAt) {}

  public record ConversationExport(String fileName, String content) {}

  public record ExportSummary(
      String chatbotName,
      String status,
      int messageCount,
      int modelCallCount,
      String latestProvider,
      String latestModel,
      int totalTokens,
      double totalEstimatedCost,
      long successModelCallCount) {}
}