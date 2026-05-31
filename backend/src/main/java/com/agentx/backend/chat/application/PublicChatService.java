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
import com.agentx.backend.deployment.application.DeploymentChannelService;
import com.agentx.backend.faq.application.FaqService;
import com.agentx.backend.knowledge.application.KnowledgeRetrievalService;
import com.agentx.backend.model.application.ModelAnswerService;
import com.agentx.backend.plan.application.PlanService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PublicChatService {

  private final ChatbotService chatbotService;
  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
    private final DeploymentChannelService deploymentChannelService;
  private final FaqService faqService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ModelAnswerService modelAnswerService;
  private final ObjectMapper objectMapper;
    private final PlanService planService;

  public PublicChatService(
      ChatbotService chatbotService,
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
            DeploymentChannelService deploymentChannelService,
      FaqService faqService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            ModelAnswerService modelAnswerService,
            ObjectMapper objectMapper,
            PlanService planService) {
    this.chatbotService = chatbotService;
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
        this.deploymentChannelService = deploymentChannelService;
    this.faqService = faqService;
    this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.modelAnswerService = modelAnswerService;
    this.objectMapper = objectMapper;
        this.planService = planService;
  }

  @Transactional
  public InitConversationResponse init(InitConversationRequest request) {
        deploymentChannelService.validatePublicAccess(request.chatbotPublicCode(), request.domain());
    PublicChatbotSnapshot snapshot =
        chatbotService.requireActiveSnapshot(request.chatbotPublicCode());
        planService.ensureTenantWithinLimit(snapshot.tenantId(), "conversations", 1);

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
                "domain", request.domain(),
                "chatbotPublicCode", snapshot.publicCode(),
                "chatbotName", snapshot.name())));
    Conversation saved = conversationRepository.save(conversation);
    deploymentChannelService.recordAccess(
        snapshot.tenantId(),
        snapshot.chatbotId(),
        saved.getId(),
        request.entryType(),
        request.domain(),
        request.ipAddress(),
        request.userAgent());
    return new InitConversationResponse(
        saved.getId(),
        saved.getAnonymousVisitorId(),
        snapshot.welcomeMessage(),
        snapshot.themeColor(),
        snapshot.brandVisible(),
        snapshot.stylePreset());
  }

  @Transactional
  public SendMessageResponse send(SendMessageRequest request) {
    PublicChatbotSnapshot snapshot =
        chatbotService.requireActiveSnapshot(request.chatbotPublicCode());
        return handleMessage(snapshot, request);
    }

    @Transactional
    public SendMessageResponse sendWithSnapshot(
            PublicChatbotSnapshot snapshot, SendMessageRequest request) {
        return handleMessage(snapshot, request);
    }

    private SendMessageResponse handleMessage(
            PublicChatbotSnapshot snapshot, SendMessageRequest request) {
        planService.ensureTenantWithinLimit(snapshot.tenantId(), "messages", 2);

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
    Message savedVisitorMessage = messageRepository.save(visitorMessage);

    FaqService.MatchResult faqMatch =
        faqService.match(
            snapshot.tenantId(), snapshot.chatbotId(), request.language(), request.message());
    KnowledgeRetrievalService.RetrievalResult knowledgeMatch =
        faqMatch.matched()
            ? new KnowledgeRetrievalService.RetrievalResult(false, null, null, null, null, 0)
            : knowledgeRetrievalService.search(
                snapshot.tenantId(), snapshot.chatbotId(), request.conversationId(), request.message());
    ResolvedReply resolvedReply = resolveReply(snapshot, request, faqMatch, knowledgeMatch);

    Message assistantMessage = new Message();
    assistantMessage.setTenantId(snapshot.tenantId());
    assistantMessage.setConversationId(conversation.getId());
    assistantMessage.setRole(MessageRole.ASSISTANT);
    assistantMessage.setStatus(MessageStatus.DELIVERED);
    assistantMessage.setContent(resolvedReply.answer());
    assistantMessage.setMetadataJson(
        toJson(
            Map.of(
                "matchedFaq", faqMatch.matched(),
                "faqId", faqMatch.faqId() == null ? "" : String.valueOf(faqMatch.faqId()),
                "knowledgeSourceId",
                knowledgeMatch.sourceId() == null ? "" : String.valueOf(knowledgeMatch.sourceId()),
                "knowledgeScore", knowledgeMatch.score(),
                "language", request.language(),
                "sourceType", resolvedReply.sourceType(),
                "citations", resolvedReply.citationMetadata(),
                "model", resolvedReply.modelMetadata())));
    Message savedAssistantMessage = messageRepository.save(assistantMessage);

    return new SendMessageResponse(
        conversation.getId(),
        savedAssistantMessage.getId(),
        String.valueOf(savedVisitorMessage.getId()),
        resolvedReply.answer(),
        resolvedReply.sourceType(),
        resolvedReply.citations());
  }

  private ResolvedReply resolveReply(
      PublicChatbotSnapshot snapshot,
      SendMessageRequest request,
      FaqService.MatchResult faqMatch,
      KnowledgeRetrievalService.RetrievalResult knowledgeMatch) {
    if (faqMatch.matched()) {
      Citation citation = new Citation(faqMatch.faqId(), faqMatch.question(), "FAQ", null);
      return new ResolvedReply(
          faqMatch.answer(), "FAQ", List.of(citation), toCitationMetadata(List.of(citation)), Map.of());
    }

    if (knowledgeMatch.matched()) {
      ModelAnswerService.ModelAnswer modelAnswer =
          modelAnswerService.generate(
              new ModelAnswerService.ModelAnswerRequest(
                  snapshot.tenantId(),
                  snapshot.chatbotId(),
                  request.conversationId(),
                  snapshot.name(),
                  request.language(),
                  request.message(),
                  knowledgeMatch.content(),
                  snapshot.providerCode(),
                  snapshot.modelCode()));
      Citation citation =
          new Citation(
              knowledgeMatch.sourceId(),
              knowledgeMatch.sourceName(),
              "KNOWLEDGE",
              knowledgeMatch.sourceLink());
      return new ResolvedReply(
          modelAnswer.answer(),
          "KNOWLEDGE",
          List.of(citation),
          toCitationMetadata(List.of(citation)),
          Map.of(
              "provider", modelAnswer.provider(),
              "model", modelAnswer.model(),
              "mode", "KNOWLEDGE_AUGMENTED",
              "promptTokens", modelAnswer.promptTokens(),
              "completionTokens", modelAnswer.completionTokens(),
              "totalTokens", modelAnswer.totalTokens(),
              "estimatedCost", modelAnswer.estimatedCost(),
              "logId", modelAnswer.logId()));
    }

    if (snapshot.allowDirectModel()) {
      ModelAnswerService.ModelAnswer modelAnswer =
          modelAnswerService.generate(
              new ModelAnswerService.ModelAnswerRequest(
                  snapshot.tenantId(),
                  snapshot.chatbotId(),
                  request.conversationId(),
                  snapshot.name(),
                  request.language(),
                  request.message(),
                  null,
                  snapshot.providerCode(),
                  snapshot.modelCode()));
      return new ResolvedReply(
          modelAnswer.answer(),
          "MODEL",
          List.of(),
          List.of(),
          Map.of(
              "provider", modelAnswer.provider(),
              "model", modelAnswer.model(),
              "mode", "DIRECT",
              "promptTokens", modelAnswer.promptTokens(),
              "completionTokens", modelAnswer.completionTokens(),
              "totalTokens", modelAnswer.totalTokens(),
              "estimatedCost", modelAnswer.estimatedCost(),
              "logId", modelAnswer.logId()));
    }

    return new ResolvedReply(snapshot.fallbackMessage(), "FALLBACK", List.of(), List.of(), Map.of());
  }

  private List<Map<String, Object>> toCitationMetadata(List<Citation> citations) {
    return citations.stream()
        .map(
            citation ->
                Map.<String, Object>of(
                    "sourceId", citation.sourceId() == null ? "" : citation.sourceId(),
                    "title", citation.title() == null ? "" : citation.title(),
                    "sourceType", citation.sourceType(),
                    "sourceLink", citation.sourceLink() == null ? "" : citation.sourceLink()))
        .toList();
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
    } catch (JacksonException exception) {
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
      Long conversationId,
      String anonymousVisitorId,
      String welcomeMessage,
      String themeColor,
      boolean brandVisible,
      String stylePreset) {}

  public record SendMessageRequest(
      Long conversationId, String chatbotPublicCode, String language, String message) {}

  public record SendMessageResponse(
      Long conversationId,
      Long assistantMessageId,
      String visitorMessageId,
      String answer,
      String sourceType,
      List<Citation> citations) {}

    public record Citation(Long sourceId, String title, String sourceType, String sourceLink) {}

  public record ConversationTranscript(
      Long conversationId, String anonymousVisitorId, List<TranscriptMessage> messages) {}

  public record TranscriptMessage(Long id, String role, String content) {}

    private record ResolvedReply(
            String answer,
            String sourceType,
            List<Citation> citations,
            List<Map<String, Object>> citationMetadata,
            Map<String, Object> modelMetadata) {}
}
