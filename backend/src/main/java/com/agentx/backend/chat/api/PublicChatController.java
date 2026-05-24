package com.agentx.backend.chat.api;

import com.agentx.backend.chat.application.PublicChatService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/chat")
public class PublicChatController {

  private final PublicChatService publicChatService;

  public PublicChatController(PublicChatService publicChatService) {
    this.publicChatService = publicChatService;
  }

  @PostMapping("/init")
  public PublicChatService.InitConversationResponse init(
      @RequestBody InitConversationRequest request) {
    return publicChatService.init(
        new PublicChatService.InitConversationRequest(
            request.chatbotPublicCode(),
            request.entryType(),
            request.domain(),
            request.ipAddress(),
            request.userAgent()));
  }

  @PostMapping("/messages")
  public PublicChatService.SendMessageResponse send(@RequestBody SendMessageRequest request) {
    return publicChatService.send(
        new PublicChatService.SendMessageRequest(
            request.conversationId(),
            request.chatbotPublicCode(),
            request.language(),
            request.message()));
  }

  @GetMapping("/conversations/{conversationId}")
  public PublicChatService.ConversationTranscript transcript(
      @PathVariable Long conversationId, @RequestParam Long tenantId) {
    return publicChatService.transcript(conversationId, tenantId);
  }

  public record InitConversationRequest(
      @NotBlank String chatbotPublicCode,
      @NotBlank String entryType,
      String domain,
      String ipAddress,
      String userAgent) {}

  public record SendMessageRequest(
      Long conversationId,
      @NotBlank String chatbotPublicCode,
      @NotBlank String language,
      @NotBlank String message) {}
}
