package com.agentx.backend.knowledge.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.knowledge.application.KnowledgeSourceService;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/knowledge-sources")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class KnowledgeSourceAdminController {

  private final KnowledgeSourceService knowledgeSourceService;

  public KnowledgeSourceAdminController(KnowledgeSourceService knowledgeSourceService) {
    this.knowledgeSourceService = knowledgeSourceService;
  }

  @GetMapping
  public List<KnowledgeSourceService.KnowledgeSourceSummary> list(
      @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return knowledgeSourceService.list(SecurityUtils.currentUser(), tenantId, chatbotId);
  }

  @GetMapping("/{sourceId}")
  public KnowledgeSourceService.KnowledgeSourceDetail get(
      @PathVariable Long sourceId, @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return knowledgeSourceService.get(SecurityUtils.currentUser(), tenantId, chatbotId, sourceId);
  }

  @PostMapping("/{sourceId}/refresh")
  public KnowledgeSourceService.KnowledgeSourceDetail refresh(
      @PathVariable Long sourceId, @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return knowledgeSourceService.refresh(SecurityUtils.currentUser(), tenantId, chatbotId, sourceId);
  }

  @PostMapping("/{sourceId}/retry")
  public KnowledgeSourceService.KnowledgeSourceDetail retry(
      @PathVariable Long sourceId, @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return knowledgeSourceService.retry(SecurityUtils.currentUser(), tenantId, chatbotId, sourceId);
  }

  @PatchMapping("/{sourceId}/status")
  public KnowledgeSourceService.KnowledgeSourceDetail updateStatus(
      @PathVariable Long sourceId,
      @RequestParam Long tenantId,
      @RequestParam Long chatbotId,
      @RequestBody UpdateStatusRequest request) {
    return knowledgeSourceService.updateStatus(
        SecurityUtils.currentUser(),
        tenantId,
        chatbotId,
        sourceId,
        KnowledgeSourceStatus.valueOf(request.status()));
  }

  @DeleteMapping("/{sourceId}")
  public KnowledgeSourceService.KnowledgeSourceDetail delete(
      @PathVariable Long sourceId, @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return knowledgeSourceService.delete(SecurityUtils.currentUser(), tenantId, chatbotId, sourceId);
  }

  @PostMapping("/upload")
  public KnowledgeSourceService.KnowledgeSourceSummary upload(
      @RequestParam Long tenantId,
      @RequestParam Long chatbotId,
      @RequestParam MultipartFile file) {
    return knowledgeSourceService.upload(SecurityUtils.currentUser(), tenantId, chatbotId, file);
  }

  @PostMapping("/web")
  public KnowledgeSourceService.KnowledgeSourceSummary createWebSource(
      @RequestParam Long tenantId,
      @RequestParam Long chatbotId,
      @RequestBody KnowledgeSourceService.CreateWebSourceRequest request) {
    return knowledgeSourceService.createWebSource(
        SecurityUtils.currentUser(), tenantId, chatbotId, request);
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleIllegalState(IllegalStateException exception) {
    if ("FILES_LIMIT_REACHED".equals(exception.getMessage())
        || "STORAGE_MB_LIMIT_REACHED".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    if ("KNOWLEDGE_FILE_UPLOAD_FAILED".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    if ("KNOWLEDGE_CHUNK_BUILD_FAILED".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    if ("KNOWLEDGE_FILE_DELETE_FAILED".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    throw exception;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
    return Map.of("code", exception.getMessage());
  }

  public record UpdateStatusRequest(String status) {}
}