package com.agentx.backend.faq.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.faq.domain.Faq;
import com.agentx.backend.faq.domain.FaqRepository;
import com.agentx.backend.faq.domain.FaqStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaqService {

  private final FaqRepository faqRepository;
  private final AuditLogService auditLogService;

  public FaqService(FaqRepository faqRepository, AuditLogService auditLogService) {
    this.faqRepository = faqRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public FaqSummary create(CurrentUser actor, CreateFaqRequest request) {
    Faq faq = new Faq();
    faq.setTenantId(request.tenantId());
    faq.setChatbotId(request.chatbotId());
    faq.setLanguage(request.language());
    faq.setStatus(FaqStatus.ACTIVE);
    faq.setQuestion(request.question());
    faq.setAlternateQuestions(String.join("\n", request.alternateQuestions()));
    faq.setAnswer(request.answer());
    Faq saved = faqRepository.save(faq);
    auditLogService.record(
        request.tenantId(),
        actor.userId(),
        "FAQ_CREATED",
        "FAQ",
        String.valueOf(saved.getId()),
        "SUCCESS",
        "LOW",
        Map.of("chatbotId", request.chatbotId()));
    return toSummary(saved);
  }

  @Transactional(readOnly = true)
  public List<FaqSummary> list(Long tenantId, Long chatbotId) {
    return faqRepository.findByTenantIdAndChatbotId(tenantId, chatbotId).stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public MatchResult match(Long tenantId, Long chatbotId, String language, String question) {
    String normalizedQuestion = normalize(question);
    return faqRepository
        .findByTenantIdAndChatbotIdAndStatus(tenantId, chatbotId, FaqStatus.ACTIVE)
        .stream()
        .filter(faq -> language == null || faq.getLanguage().equalsIgnoreCase(language))
        .filter(faq -> matches(faq, normalizedQuestion))
        .findFirst()
        .map(faq -> new MatchResult(true, faq.getId(), faq.getQuestion(), faq.getAnswer()))
        .orElseGet(() -> new MatchResult(false, null, null, null));
  }

  private boolean matches(Faq faq, String normalizedQuestion) {
    if (normalize(faq.getQuestion()).equals(normalizedQuestion)) {
      return true;
    }
    return Arrays.stream(
            faq.getAlternateQuestions() == null
                ? new String[0]
                : faq.getAlternateQuestions().split("\\n"))
        .map(this::normalize)
        .anyMatch(normalizedQuestion::equals);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private FaqSummary toSummary(Faq faq) {
    return new FaqSummary(
        faq.getId(),
        faq.getTenantId(),
        faq.getChatbotId(),
        faq.getLanguage(),
        faq.getStatus(),
        faq.getQuestion(),
        faq.getAlternateQuestions() == null
            ? List.of()
            : Arrays.asList(faq.getAlternateQuestions().split("\\n")),
        faq.getAnswer());
  }

  public record CreateFaqRequest(
      Long tenantId,
      Long chatbotId,
      String language,
      String question,
      List<String> alternateQuestions,
      String answer) {}

  public record FaqSummary(
      Long id,
      Long tenantId,
      Long chatbotId,
      String language,
      FaqStatus status,
      String question,
      List<String> alternateQuestions,
      String answer) {}

  public record MatchResult(boolean matched, Long faqId, String question, String answer) {}
}
