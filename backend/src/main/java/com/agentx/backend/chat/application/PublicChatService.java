package com.agentx.backend.chat.application;

import com.agentx.backend.chatbot.application.ChatbotService;
import com.agentx.backend.chatbot.application.ChatbotService.PublicChatbotSnapshot;
import com.agentx.backend.conversation.domain.Conversation;
import com.agentx.backend.conversation.domain.ConversationRepository;
import com.agentx.backend.conversation.domain.ConversationStatus;
import com.agentx.backend.conversation.domain.Message;
import com.agentx.backend.conversation.domain.MessageRepository;
import com.agentx.backend.conversation.domain.MessageRole;
import com.agentx.backend.conversation.domain.MessageStatus;
import com.agentx.backend.faq.application.FaqService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicChatService {

  private final ChatbotService chatbotService;
  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final FaqService faqService;
  private final ObjectMapper objectMapper;

  public PublicChatService(
      ChatbotService chatbotService,
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      FaqService faqService,
      ObjectMapper objectMapper) {
    this.chatbotService = chatbotService;
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.faqService = faqService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public InitConversationResponse init(InitConversationRequest request) {
    PublicChatbotSnapshot snapshot =
        chatbotService.requireActiveSnapshot(request.chatbotPublicCode());
    Conversation conversation = new Conversation();
    conversation.setTenantId(snapshot.tenantId());
    conversation.setChatbotId(snapshot.chatbotId());
    conversation.setAnonymousVisitorId(UUID.randomUUID().toString());
    conversation.setEntryType(request.entryType());
    conversation.setStatus(ConversationStatus.ACTIVE);
    conversation.setMetadataJson(
        toJson(
            Map.of(
                "ip", request.ipAddress(),
                "userAgent", request.userAgent(),
                "domain", request.domain())));
    Conversation saved = conversationRepository.save(conversation);
    return new InitConversationResponse(
        saved.getId(),
        saved.getAnonymousVisitorId(),
        snapshot.welcomeMessage(),
        snapshot.themeColor());
  }

  @Transactional
  public SendMessageResponse send(SendMessageRequest request) {
    PublicChatbotSnapshot snapshot =
        chatbotService.requireActiveSnapshot(request.chatbotPublicCode());
    Conversation conversation =
        conversationRepository
            .findByIdAndTenantId(request.conversationId(), snapshot.tenantId())
            .orElseThrow();

    Message visitorMessage = new Message();
    visitorMessage.setTenantId(snapshot.tenantId());
    visitorMessage.setConversationId(conversation.getId());
    visitorMessage.setRole(MessageRole.VISITOR);
    visitorMessage.setStatus(MessageStatus.DELIVERED);
    visitorMessage.setContent(request.message());
    visitorMessage.setMetadataJson(toJson(Map.of("language", request.language())));
    messageRepository.save(visitorMessage);

    FaqService.MatchResult faqMatch =
        faqService.match(
            snapshot.tenantId(), snapshot.chatbotId(), request.language(), request.message());
    String answer = faqMatch.matched() ? faqMatch.answer() : snapshot.fallbackMessage();

    Message assistantMessage = new Message();
    assistantMessage.setTenantId(snapshot.tenantId());
    assistantMessage.setConversationId(conversation.getId());
    assistantMessage.setRole(MessageRole.ASSISTANT);
    assistantMessage.setStatus(MessageStatus.DELIVERED);
    assistantMessage.setContent(answer);
    assistantMessage.setMetadataJson(
        toJson(
            Map.of(
                "matchedFaq", faqMatch.matched(),
                "faqId", faqMatch.faqId() == null ? "" : String.valueOf(faqMatch.faqId()),
                "sourceType", faqMatch.matched() ? "FAQ" : "FALLBACK")));
    Message savedAssistantMessage = messageRepository.save(assistantMessage);

    return new SendMessageResponse(
        conversation.getId(),
        savedAssistantMessage.getId(),
        answer,
        faqMatch.matched() ? "FAQ" : "FALLBACK",
        faqMatch.matched()
            ? List.of(new Citation(faqMatch.faqId(), faqMatch.question(), "FAQ"))
            : List.of());
  }

  @Transactional(readOnly = true)
  public ConversationTranscript transcript(Long conversationId, Long tenantId) {
    Conversation conversation =
        conversationRepository.findByIdAndTenantId(conversationId, tenantId).orElseThrow();
    List<TranscriptMessage> messages =
        messageRepository.findByConversationIdOrderByIdAsc(conversation.getId()).stream()
            .map(
                message ->
                    new TranscriptMessage(
                        message.getId(), message.getRole().name(), message.getContent()))
            .toList();
    return new ConversationTranscript(
        conversation.getId(), conversation.getAnonymousVisitorId(), messages);
  }

  private String toJson(Map<String, Object> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize chat payload", exception);
    }
  }

  public record InitConversationRequest(
      String chatbotPublicCode,
      String entryType,
      String domain,
      String ipAddress,
      String userAgent) {}

  public record InitConversationResponse(
      Long conversationId, String anonymousVisitorId, String welcomeMessage, String themeColor) {}

  public record SendMessageRequest(
      Long conversationId, String chatbotPublicCode, String language, String message) {}

  public record SendMessageResponse(
      Long conversationId,
      Long assistantMessageId,
      String answer,
      String sourceType,
      List<Citation> citations) {}

  public record Citation(Long sourceId, String title, String sourceType) {}

  public record ConversationTranscript(
      Long conversationId, String anonymousVisitorId, List<TranscriptMessage> messages) {}

  public record TranscriptMessage(Long id, String role, String content) {}
}
