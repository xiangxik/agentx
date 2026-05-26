package com.agentx.backend.chat.api;

import com.agentx.backend.chat.application.PublicChatService;
import com.agentx.backend.chatbot.application.ChatbotService;
import com.agentx.backend.chatbot.application.ChatbotService.PublicChatbotSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicChatWebSocketHandler extends TextWebSocketHandler {

  private final ChatbotService chatbotService;
  private final PublicChatService publicChatService;
  private final ObjectMapper objectMapper;

  public PublicChatWebSocketHandler(
      ChatbotService chatbotService,
      PublicChatService publicChatService,
      ObjectMapper objectMapper) {
    this.chatbotService = chatbotService;
    this.publicChatService = publicChatService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
    HandshakeContext context = resolveContext(session);
    session.getAttributes().put("chatContext", context);
    send(
        session,
        new ServerEvent(
            "CONNECTED",
            context.conversationId(),
            null,
            null,
            null,
            List.of(),
            null,
            null,
            "WebSocket connected"));
  }

  @Override
  protected void handleTextMessage(
      @NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
    ChatClientMessage payload = readClientMessage(message.getPayload());
    HandshakeContext context = (HandshakeContext) session.getAttributes().get("chatContext");

    if (context == null) {
      context = resolveContext(session);
      session.getAttributes().put("chatContext", context);
    }

    if (!Objects.equals(payload.conversationId(), context.conversationId())) {
      sendError(session, context.conversationId(), payload.clientMessageId(), "会话不匹配");
      return;
    }

    if (payload.message() == null || payload.message().trim().isEmpty()) {
      sendError(session, context.conversationId(), payload.clientMessageId(), "消息内容不能为空");
      return;
    }

    send(
        session,
        new ServerEvent(
            "PROCESSING",
            context.conversationId(),
            payload.clientMessageId(),
            null,
            null,
            List.of(),
            null,
            null,
            null));

    try {
      PublicChatService.SendMessageResponse response =
          publicChatService.sendWithSnapshot(
              context.snapshot(),
              new PublicChatService.SendMessageRequest(
                  context.conversationId(),
                  context.snapshot().publicCode(),
                  Optional.ofNullable(payload.language()).filter(value -> !value.isBlank()).orElse("zh-CN"),
                  payload.message().trim()));

      send(
          session,
          new ServerEvent(
              "MESSAGE_COMPLETED",
              response.conversationId(),
              payload.clientMessageId(),
              response.visitorMessageId(),
              response.assistantMessageId(),
              response.citations(),
              response.answer(),
              response.sourceType(),
              null));
    } catch (IllegalStateException exception) {
      sendError(session, context.conversationId(), payload.clientMessageId(), mapError(exception));
    }
  }

  @Override
  public void handleTransportError(
      @NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
    sendError(session, null, null, exception.getMessage() == null ? "连接异常" : exception.getMessage());
    session.close(Objects.requireNonNull(CloseStatus.SERVER_ERROR));
  }

  private HandshakeContext resolveContext(WebSocketSession session) {
    MultiValueMap<String, String> query = parseQueryParams(session.getUri());
    String publicCode = firstRequired(query, "chatbotPublicCode");
    Long conversationId = Long.valueOf(firstRequired(query, "conversationId"));
    PublicChatbotSnapshot snapshot = chatbotService.requireActiveSnapshot(publicCode);
    return new HandshakeContext(conversationId, snapshot);
  }

  private ChatClientMessage readClientMessage(String payload) throws JacksonException {
    return objectMapper.readValue(payload, ChatClientMessage.class);
  }

  private void send(WebSocketSession session, ServerEvent event) throws IOException {
    session.sendMessage(new TextMessage(Objects.requireNonNull(objectMapper.writeValueAsString(event))));
  }

  private void sendError(
      WebSocketSession session, Long conversationId, String clientMessageId, String message)
      throws IOException {
    send(
        session,
        new ServerEvent(
            "ERROR",
            conversationId,
            clientMessageId,
            null,
            null,
            List.of(),
            null,
            null,
            message));
  }

  private String mapError(IllegalStateException exception) {
    return switch (exception.getMessage()) {
      case "CONVERSATIONS_LIMIT_REACHED" -> "当前租户的会话额度已达上限。";
      case "MESSAGES_LIMIT_REACHED" -> "当前租户的消息额度已达上限。";
      case "CHATBOT_NOT_ACTIVE" -> "当前 Chatbot 未启用。";
      default -> exception.getMessage() == null ? "消息发送失败" : exception.getMessage();
    };
  }

  private MultiValueMap<String, String> parseQueryParams(URI uri) {
    LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
      return params;
    }

    for (String segment : uri.getQuery().split("&")) {
      String[] pair = segment.split("=", 2);
      if (pair.length == 2) {
        params.add(
            Objects.requireNonNull(pair[0]),
            Objects.requireNonNull(URLDecoder.decode(Objects.requireNonNull(pair[1]), StandardCharsets.UTF_8)));
      }
    }
    return params;
  }

  private String firstRequired(MultiValueMap<String, String> params, String key) {
    List<String> values = params.get(key);
    if (values == null || values.isEmpty()) {
      throw new IllegalStateException("Missing websocket parameter: " + key);
    }

    String value = Objects.requireNonNull(values.get(0));
    if (value.isBlank()) {
      throw new IllegalStateException("Missing websocket parameter: " + key);
    }
    return value;
  }

  private record HandshakeContext(Long conversationId, PublicChatbotSnapshot snapshot) {}

  public record ChatClientMessage(Long conversationId, String clientMessageId, String language, String message) {}

  public record ServerEvent(
      String type,
      Long conversationId,
      String clientMessageId,
      String visitorMessageId,
      Long assistantMessageId,
      List<PublicChatService.Citation> citations,
      String answer,
      String sourceType,
      String errorMessage) {}
}