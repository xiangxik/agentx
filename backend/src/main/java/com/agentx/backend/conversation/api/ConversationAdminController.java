package com.agentx.backend.conversation.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.conversation.application.ConversationAdminService;
import com.agentx.backend.conversation.domain.ConversationStatus;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversations")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class ConversationAdminController {

  private final ConversationAdminService conversationAdminService;

  public ConversationAdminController(ConversationAdminService conversationAdminService) {
    this.conversationAdminService = conversationAdminService;
  }

  @GetMapping
  public List<ConversationAdminService.ConversationSummary> list(
      @RequestParam(required = false) Long chatbotId, @RequestParam(required = false) String status) {
    return conversationAdminService.list(
        SecurityUtils.currentUser(),
        chatbotId,
        status == null || status.isBlank() ? null : ConversationStatus.valueOf(status));
  }

  @GetMapping("/{conversationId}")
  public ConversationAdminService.ConversationDetail get(@PathVariable Long conversationId) {
    return conversationAdminService.get(SecurityUtils.currentUser(), conversationId);
  }

  @GetMapping("/{conversationId}/export")
  public ResponseEntity<byte[]> export(@PathVariable Long conversationId) {
    ConversationAdminService.ConversationExport export =
        conversationAdminService.export(SecurityUtils.currentUser(), conversationId);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(export.fileName()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(export.content().getBytes(StandardCharsets.UTF_8));
  }

  @PatchMapping("/{conversationId}/status")
  public ConversationAdminService.ConversationSummary updateStatus(
      @PathVariable Long conversationId, @RequestBody UpdateStatusRequest request) {
    return conversationAdminService.updateStatus(
        SecurityUtils.currentUser(), conversationId, ConversationStatus.valueOf(request.status()));
  }

  @DeleteMapping("/{conversationId}")
  public ConversationAdminService.ConversationSummary delete(@PathVariable Long conversationId) {
    return conversationAdminService.delete(SecurityUtils.currentUser(), conversationId);
  }

  public record UpdateStatusRequest(@NotBlank String status) {}
}