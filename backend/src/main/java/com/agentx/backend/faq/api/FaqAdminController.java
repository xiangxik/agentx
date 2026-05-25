package com.agentx.backend.faq.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.faq.application.FaqService;
import com.agentx.backend.faq.domain.FaqStatus;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/faqs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class FaqAdminController {

  private final FaqService faqService;

  public FaqAdminController(FaqService faqService) {
    this.faqService = faqService;
  }

  @GetMapping
  public List<FaqService.FaqSummary> list(
      @RequestParam Long tenantId,
      @RequestParam Long chatbotId,
      @RequestParam(required = false) String language,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    return faqService.list(
        tenantId,
        chatbotId,
        language,
        keyword,
        status == null || status.isBlank() ? null : FaqStatus.valueOf(status));
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    FaqService.FaqExport export = faqService.export(SecurityUtils.currentUser(), tenantId, chatbotId);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(export.fileName()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(export.content().getBytes(StandardCharsets.UTF_8));
  }

  @PostMapping
  public FaqService.FaqSummary create(@RequestBody CreateFaqRequest request) {
    return faqService.create(
        SecurityUtils.currentUser(),
        new FaqService.CreateFaqRequest(
            request.tenantId(),
            request.chatbotId(),
            request.language(),
            request.question(),
            request.alternateQuestions(),
            request.answer()));
  }

        @PostMapping("/import")
        public FaqService.FaqImportResult importFaqs(@RequestBody ImportFaqRequest request) {
        return faqService.importFaqs(
          SecurityUtils.currentUser(),
          new FaqService.ImportFaqRequest(
            request.tenantId(),
            request.chatbotId(),
            request.items().stream()
              .map(
                item ->
                  new FaqService.ImportFaqItem(
                    item.language(),
                    item.status() == null || item.status().isBlank()
                      ? null
                      : FaqStatus.valueOf(item.status()),
                    item.question(),
                    item.alternateQuestions(),
                    item.answer()))
              .toList()));
        }

  @PatchMapping("/{faqId}")
  public FaqService.FaqSummary update(
      @PathVariable Long faqId, @RequestBody UpdateFaqRequest request) {
    return faqService.update(
        SecurityUtils.currentUser(),
        faqId,
        new FaqService.UpdateFaqRequest(
            request.language(),
            request.question(),
            request.alternateQuestions(),
            request.answer()));
  }

  @PatchMapping("/{faqId}/status")
  public FaqService.FaqSummary updateStatus(
      @PathVariable Long faqId, @RequestBody UpdateStatusRequest request) {
    return faqService.updateStatus(
        SecurityUtils.currentUser(), faqId, FaqStatus.valueOf(request.status()));
  }

  @PatchMapping("/status")
  public List<FaqService.FaqSummary> updateStatuses(@RequestBody BatchUpdateStatusRequest request) {
    return faqService.updateStatuses(
        SecurityUtils.currentUser(), request.faqIds(), FaqStatus.valueOf(request.status()));
  }

  public record CreateFaqRequest(
      Long tenantId,
      Long chatbotId,
      @NotBlank String language,
      @NotBlank String question,
      List<String> alternateQuestions,
      @NotBlank String answer) {}

      public record UpdateFaqRequest(
        @NotBlank String language,
        @NotBlank String question,
        List<String> alternateQuestions,
        @NotBlank String answer) {}

      public record UpdateStatusRequest(@NotBlank String status) {}

      public record BatchUpdateStatusRequest(List<Long> faqIds, @NotBlank String status) {}

      public record ImportFaqRequest(Long tenantId, Long chatbotId, List<ImportFaqItem> items) {}

      public record ImportFaqItem(
          String language,
          String status,
          @NotBlank String question,
          List<String> alternateQuestions,
          @NotBlank String answer) {}
}
