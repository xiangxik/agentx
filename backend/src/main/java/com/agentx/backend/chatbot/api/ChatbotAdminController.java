package com.agentx.backend.chatbot.api;

import com.agentx.backend.chatbot.application.ChatbotService;
import com.agentx.backend.chatbot.domain.ChatbotStatus;
import com.agentx.backend.common.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  @GetMapping("/{chatbotId}")
  public ChatbotService.ChatbotDetail get(@PathVariable Long chatbotId) {
    return chatbotService.get(SecurityUtils.currentUser(), chatbotId);
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

  @PatchMapping("/{chatbotId}")
  public ChatbotService.ChatbotSummary update(
      @PathVariable Long chatbotId, @RequestBody UpdateChatbotRequest request) {
    return chatbotService.update(
        SecurityUtils.currentUser(),
        chatbotId,
        new ChatbotService.UpdateChatbotRequest(
            request.name(),
            request.description(),
            request.language(),
            ChatbotStatus.valueOf(request.status())));
  }

  @PatchMapping("/{chatbotId}/status")
  public ChatbotService.ChatbotSummary updateStatus(
      @PathVariable Long chatbotId, @RequestBody UpdateStatusRequest request) {
    return chatbotService.updateStatus(
        SecurityUtils.currentUser(), chatbotId, ChatbotStatus.valueOf(request.status()));
  }

  @PatchMapping("/{chatbotId}/appearance")
  public ChatbotService.ChatbotDetail updateAppearance(
      @PathVariable Long chatbotId, @RequestBody UpdateAppearanceRequest request) {
    return chatbotService.updateAppearance(
        SecurityUtils.currentUser(),
        chatbotId,
        new ChatbotService.UpdateAppearanceRequest(
            request.themeColor(),
            request.welcomeMessage(),
            request.brandVisible(),
          request.launcherPosition(),
          request.stylePreset()));
  }

  @PatchMapping("/{chatbotId}/behavior")
  public ChatbotService.ChatbotDetail updateBehavior(
      @PathVariable Long chatbotId, @RequestBody UpdateBehaviorRequest request) {
    return chatbotService.updateBehavior(
        SecurityUtils.currentUser(),
        chatbotId,
        new ChatbotService.UpdateBehaviorRequest(
            request.fallbackMessage(),
            request.allowDirectModel(),
            request.allowFeedback(),
            request.allowHandoff()));
  }

  @PostMapping("/{chatbotId}/copy")
  public ChatbotService.ChatbotDetail copy(@PathVariable Long chatbotId) {
    return chatbotService.copy(SecurityUtils.currentUser(), chatbotId);
  }

  @DeleteMapping("/{chatbotId}")
  public ChatbotService.ChatbotSummary delete(@PathVariable Long chatbotId) {
    return chatbotService.delete(SecurityUtils.currentUser(), chatbotId);
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleIllegalState(IllegalStateException exception) {
    if ("CHATBOTS_LIMIT_REACHED".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    throw exception;
  }

  public record CreateChatbotRequest(
      Long tenantId,
      @NotBlank String name,
      String description,
      @NotBlank String language,
      @NotBlank String status) {}

      public record UpdateChatbotRequest(
        @NotBlank String name,
        String description,
        @NotBlank String language,
        @NotBlank String status) {}

      public record UpdateStatusRequest(@NotBlank String status) {}

    public record UpdateAppearanceRequest(
        @NotBlank String themeColor,
        @NotBlank String welcomeMessage,
        boolean brandVisible,
      @NotBlank String launcherPosition,
      @NotBlank String stylePreset) {}

    public record UpdateBehaviorRequest(
        @NotBlank String fallbackMessage,
        boolean allowDirectModel,
        boolean allowFeedback,
        boolean allowHandoff) {}
}
