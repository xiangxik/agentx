package com.agentx.backend.faq.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.faq.application.FaqService;
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
@RequestMapping("/api/admin/faqs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class FaqAdminController {

  private final FaqService faqService;

  public FaqAdminController(FaqService faqService) {
    this.faqService = faqService;
  }

  @GetMapping
  public List<FaqService.FaqSummary> list(
      @RequestParam Long tenantId, @RequestParam Long chatbotId) {
    return faqService.list(tenantId, chatbotId);
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

  public record CreateFaqRequest(
      Long tenantId,
      Long chatbotId,
      @NotBlank String language,
      @NotBlank String question,
      List<String> alternateQuestions,
      @NotBlank String answer) {}
}
