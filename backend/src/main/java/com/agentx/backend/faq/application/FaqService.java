package com.agentx.backend.faq.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.faq.domain.Faq;
import com.agentx.backend.faq.domain.FaqRepository;
import com.agentx.backend.faq.domain.FaqStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FaqService {

  private final FaqRepository faqRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;
  private final ChatbotRepository chatbotRepository;

  public FaqService(
      FaqRepository faqRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper,
      ChatbotRepository chatbotRepository) {
    this.faqRepository = faqRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
    this.chatbotRepository = chatbotRepository;
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
  public List<FaqSummary> list(Long tenantId, Long chatbotId, String language, String keyword, FaqStatus status) {
    return faqRepository.findByTenantIdAndChatbotId(tenantId, chatbotId).stream()
        .filter(faq -> language == null || language.isBlank() || faq.getLanguage().equalsIgnoreCase(language))
        .filter(faq -> status == null || faq.getStatus() == status)
        .filter(
            faq -> {
              if (keyword == null || keyword.isBlank()) {
                return true;
              }

              String normalizedKeyword = normalize(keyword);
              return normalize(faq.getQuestion()).contains(normalizedKeyword)
                  || normalize(faq.getAnswer()).contains(normalizedKeyword)
                  || Arrays.stream(
                          faq.getAlternateQuestions() == null
                              ? new String[0]
                              : faq.getAlternateQuestions().split("\\n"))
                      .map(this::normalize)
                      .anyMatch(item -> item.contains(normalizedKeyword));
            })
        .map(this::toSummary)
        .toList();
  }

  @Transactional
  public FaqSummary update(CurrentUser actor, Long faqId, UpdateFaqRequest request) {
    Faq faq = loadFaq(actor, faqId);
    faq.setLanguage(request.language());
    faq.setQuestion(request.question());
    faq.setAlternateQuestions(String.join("\n", request.alternateQuestions()));
    faq.setAnswer(request.answer());
    auditLogService.record(
        faq.getTenantId(),
        actor.userId(),
        "FAQ_UPDATED",
        "FAQ",
        String.valueOf(faq.getId()),
        "SUCCESS",
        "LOW",
        Map.of("chatbotId", faq.getChatbotId()));
    return toSummary(faq);
  }

  @Transactional
  public FaqSummary updateStatus(CurrentUser actor, Long faqId, FaqStatus status) {
    Faq faq = loadFaq(actor, faqId);
    faq.setStatus(status);
    auditLogService.record(
        faq.getTenantId(),
        actor.userId(),
        "FAQ_STATUS_UPDATED",
        "FAQ",
        String.valueOf(faq.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("status", status.name()));
    return toSummary(faq);
  }

  @Transactional
  public List<FaqSummary> updateStatuses(CurrentUser actor, List<Long> faqIds, FaqStatus status) {
    return faqIds.stream().map(faqId -> updateStatus(actor, faqId, status)).toList();
  }

  @Transactional(readOnly = true)
  public FaqExport export(CurrentUser actor, Long tenantId, Long chatbotId) {
    List<FaqSummary> faqs = list(tenantId, chatbotId, null, null, null);

    try {
      String content =
          objectMapper.writeValueAsString(
              Map.of(
                  "exportedAt", Instant.now().toString(),
                  "tenantId", tenantId,
                  "chatbotId", chatbotId,
                  "items", faqs));

      auditLogService.record(
          tenantId,
          actor.userId(),
          "FAQ_EXPORTED",
          "FAQ",
          "%d:%d".formatted(tenantId, chatbotId),
          "SUCCESS",
          "MEDIUM",
          Map.of("count", faqs.size()));

      return new FaqExport("faq-%d-%d.json".formatted(tenantId, chatbotId), content);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize FAQ export", exception);
    }
  }

  @Transactional
  public FaqImportResult importFaqs(CurrentUser actor, ImportFaqRequest request) {
    validateChatbotAccess(actor, request.tenantId(), request.chatbotId());

    int importedCount = 0;
    List<FaqImportFailure> failures = new java.util.ArrayList<>();

    for (int index = 0; index < request.items().size(); index++) {
      ImportFaqItem item = request.items().get(index);

      if (item.question() == null || item.question().isBlank()) {
        failures.add(new FaqImportFailure(index, "question", "问题不能为空"));
        continue;
      }

      if (item.answer() == null || item.answer().isBlank()) {
        failures.add(new FaqImportFailure(index, "answer", "答案不能为空"));
        continue;
      }

      Faq faq = new Faq();
      faq.setTenantId(request.tenantId());
      faq.setChatbotId(request.chatbotId());
      faq.setLanguage(item.language() == null || item.language().isBlank() ? "zh-CN" : item.language());
      faq.setStatus(item.status() == null ? FaqStatus.ACTIVE : item.status());
      faq.setQuestion(item.question().trim());
      faq.setAlternateQuestions(String.join("\n", item.alternateQuestions() == null ? List.of() : item.alternateQuestions()));
      faq.setAnswer(item.answer().trim());
      faqRepository.save(faq);
      importedCount++;
    }

    auditLogService.record(
        request.tenantId(),
        actor.userId(),
        "FAQ_IMPORTED",
        "FAQ",
        "%d:%d".formatted(request.tenantId(), request.chatbotId()),
        failures.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS",
        "MEDIUM",
        Map.of("importedCount", importedCount, "failureCount", failures.size()));

    return new FaqImportResult(importedCount, failures);
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

  private Faq loadFaq(CurrentUser actor, Long faqId) {
    if (actor.isSuperAdmin()) {
      return faqRepository.findById(faqId).orElseThrow();
    }

    return faqRepository.findByIdAndTenantId(faqId, actor.tenantId()).orElseThrow();
  }

  private void validateChatbotAccess(CurrentUser actor, Long tenantId, Long chatbotId) {
    boolean allowed =
        actor.isSuperAdmin()
            ? chatbotRepository.findByIdAndTenantId(chatbotId, tenantId).isPresent()
            : actor.tenantId().equals(tenantId)
                && chatbotRepository.findByIdAndTenantId(chatbotId, actor.tenantId()).isPresent();

    if (!allowed) {
      throw new IllegalArgumentException("Chatbot not found");
    }
  }

  public record CreateFaqRequest(
      Long tenantId,
      Long chatbotId,
      String language,
      String question,
      List<String> alternateQuestions,
      String answer) {}

    public record UpdateFaqRequest(
      String language, String question, List<String> alternateQuestions, String answer) {}

  public record FaqSummary(
      Long id,
      Long tenantId,
      Long chatbotId,
      String language,
      FaqStatus status,
      String question,
      List<String> alternateQuestions,
      String answer) {}

  public record FaqExport(String fileName, String content) {}

  public record ImportFaqRequest(Long tenantId, Long chatbotId, List<ImportFaqItem> items) {}

  public record ImportFaqItem(
      String language, FaqStatus status, String question, List<String> alternateQuestions, String answer) {}

  public record FaqImportFailure(int index, String field, String reason) {}

  public record FaqImportResult(int importedCount, List<FaqImportFailure> failures) {}

  public record MatchResult(boolean matched, Long faqId, String question, String answer) {}
}
