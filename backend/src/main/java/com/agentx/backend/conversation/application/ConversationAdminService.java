package com.agentx.backend.conversation.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.conversation.domain.Conversation;
import com.agentx.backend.conversation.domain.ConversationRepository;
import com.agentx.backend.conversation.domain.ConversationStatus;
import com.agentx.backend.conversation.domain.Message;
import com.agentx.backend.conversation.domain.MessageRepository;
import java.time.Instant;
import java.util.List;
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
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public ConversationAdminService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      ChatbotRepository chatbotRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.chatbotRepository = chatbotRepository;
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
        fromJson(conversation.getMetadataJson()),
        messages);
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

    try {
      String content =
          objectMapper.writeValueAsString(
              Map.of(
                  "exportedAt", Instant.now().toString(),
                  "conversation", detail));

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
          "conversation-%d.json".formatted(detail.id()), content);
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
    return new ConversationMessage(
        message.getId(),
        message.getRole().name(),
        message.getStatus().name(),
        message.getContent(),
        fromJson(message.getMetadataJson()),
        message.getCreatedAt().toString());
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
      Map<String, Object> metadata,
      List<ConversationMessage> messages) {}

  public record ConversationMessage(
      Long id,
      String role,
      String status,
      String content,
      Map<String, Object> metadata,
      String createdAt) {}

  public record ConversationExport(String fileName, String content) {}
}