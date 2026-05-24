package com.agentx.backend.chatbot.api;

import com.agentx.backend.chatbot.application.ChatbotService;
import com.agentx.backend.chatbot.domain.ChatbotStatus;
import com.agentx.backend.common.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chatbots")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class ChatbotAdminController {

  private final ChatbotService chatbotService;

  public ChatbotAdminController(ChatbotService chatbotService) {
    this.chatbotService = chatbotService;
  }

  @GetMapping
  public List<ChatbotService.ChatbotSummary> list(@RequestParam Long tenantId) {
    return chatbotService.listByTenant(tenantId);
  }

  @PostMapping
  public ChatbotService.ChatbotSummary create(@RequestBody CreateChatbotRequest request) {
    return chatbotService.create(
        SecurityUtils.currentUser(),
        new ChatbotService.CreateChatbotRequest(
            request.tenantId(),
            request.name(),
            request.description(),
            request.language(),
            ChatbotStatus.valueOf(request.status())));
  }

  public record CreateChatbotRequest(
      Long tenantId,
      @NotBlank String name,
      String description,
      @NotBlank String language,
      @NotBlank String status) {}
}
