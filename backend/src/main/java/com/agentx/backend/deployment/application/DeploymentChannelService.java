package com.agentx.backend.deployment.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.deployment.domain.DeploymentAccessLog;
import com.agentx.backend.deployment.domain.DeploymentAccessLogRepository;
import com.agentx.backend.chatbot.domain.Chatbot;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.chatbot.domain.ChatbotStatus;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.deployment.domain.DeploymentWhitelistDomain;
import com.agentx.backend.deployment.domain.DeploymentWhitelistDomainRepository;
import com.agentx.backend.tenant.domain.Tenant;
import com.agentx.backend.tenant.domain.TenantRepository;
import com.agentx.backend.tenant.domain.TenantStatus;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentChannelService {

  private final ChatbotRepository chatbotRepository;
  private final TenantRepository tenantRepository;
  private final DeploymentAccessLogRepository accessLogRepository;
  private final DeploymentWhitelistDomainRepository whitelistDomainRepository;
  private final AuditLogService auditLogService;
  private final String publicBaseUrl;

  public DeploymentChannelService(
      ChatbotRepository chatbotRepository,
      TenantRepository tenantRepository,
      DeploymentAccessLogRepository accessLogRepository,
      DeploymentWhitelistDomainRepository whitelistDomainRepository,
      AuditLogService auditLogService,
      @Value("${agentx.public-base-url:http://localhost:5173}") String publicBaseUrl) {
    this.chatbotRepository = chatbotRepository;
    this.tenantRepository = tenantRepository;
    this.accessLogRepository = accessLogRepository;
    this.whitelistDomainRepository = whitelistDomainRepository;
    this.auditLogService = auditLogService;
    this.publicBaseUrl =
        publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
  }

  @Transactional(readOnly = true)
  public DeploymentOverview getOverview(CurrentUser actor, Long chatbotId) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    return toOverview(chatbot);
  }

  @Transactional(readOnly = true)
  public List<WhitelistDomainSummary> listWhitelistDomains(CurrentUser actor, Long chatbotId) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    return whitelistDomainRepository.findByChatbotIdOrderByDomainAsc(chatbot.getId()).stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional
  public WhitelistDomainSummary addWhitelistDomain(CurrentUser actor, Long chatbotId, String domain) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    String normalizedDomain = normalizeDomain(domain);

    return whitelistDomainRepository.findByChatbotIdAndDomain(chatbot.getId(), normalizedDomain)
        .map(this::toSummary)
        .orElseGet(
            () -> {
              DeploymentWhitelistDomain entity = new DeploymentWhitelistDomain();
              entity.setTenantId(chatbot.getTenantId());
              entity.setChatbotId(chatbot.getId());
              entity.setDomain(normalizedDomain);
              DeploymentWhitelistDomain saved = whitelistDomainRepository.save(entity);
              auditLogService.record(
                  chatbot.getTenantId(),
                  actor.userId(),
                  "DEPLOYMENT_DOMAIN_ADDED",
                  "CHATBOT",
                  String.valueOf(chatbot.getId()),
                  "SUCCESS",
                  "MEDIUM",
                  Map.of("domain", normalizedDomain));
              return toSummary(saved);
            });
  }

  @Transactional
  public void deleteWhitelistDomain(CurrentUser actor, Long chatbotId, Long domainId) {
    Chatbot chatbot = loadChatbot(actor, chatbotId);
    DeploymentWhitelistDomain entity =
        whitelistDomainRepository
            .findById(domainId)
            .filter(item -> item.getChatbotId().equals(chatbot.getId()))
            .orElseThrow();
    whitelistDomainRepository.delete(entity);
    auditLogService.record(
        chatbot.getTenantId(),
        actor.userId(),
        "DEPLOYMENT_DOMAIN_REMOVED",
        "CHATBOT",
        String.valueOf(chatbot.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("domain", entity.getDomain()));
  }

  @Transactional(readOnly = true)
  public void validatePublicAccess(String publicCode, String domain) {
    Chatbot chatbot =
        chatbotRepository
            .findByPublicCode(publicCode)
            .orElseThrow(() -> new IllegalArgumentException("CHATBOT_NOT_FOUND"));
    if (chatbot.getStatus() != ChatbotStatus.ACTIVE) {
      throw new IllegalStateException("CHATBOT_NOT_ACTIVE");
    }

    Tenant tenant = tenantRepository.findById(chatbot.getTenantId()).orElseThrow();
    if (tenant.getStatus() != TenantStatus.ACTIVE) {
      throw new IllegalStateException("TENANT_NOT_ACTIVE");
    }

    List<DeploymentWhitelistDomain> allowedDomains =
        whitelistDomainRepository.findByChatbotIdOrderByDomainAsc(chatbot.getId());
    if (allowedDomains.isEmpty()) {
      return;
    }

    String normalizedDomain = normalizeDomain(domain);
    boolean matched =
        allowedDomains.stream().anyMatch(item -> item.getDomain().equals(normalizedDomain));
    if (!matched) {
      throw new IllegalStateException("DOMAIN_NOT_ALLOWED");
    }
  }

  @Transactional
  public void recordAccess(
      Long tenantId,
      Long chatbotId,
      Long conversationId,
      String entryType,
      String domain,
      String ipAddress,
      String userAgent) {
    DeploymentAccessLog log = new DeploymentAccessLog();
    log.setTenantId(tenantId);
    log.setChatbotId(chatbotId);
    log.setConversationId(conversationId);
    log.setEntryType(entryType);
    log.setDomainName(normalizeDomainForLog(domain));
    log.setIpAddress(blankToNull(ipAddress));
    log.setUserAgent(blankToNull(userAgent));
    accessLogRepository.save(log);
  }

  private DeploymentOverview toOverview(Chatbot chatbot) {
    String publicCode = chatbot.getPublicCode();
    String widgetScriptUrl = publicBaseUrl + "/widget/sdk.js";
    String chatPageUrl = publicBaseUrl + "/chat-page?bot=" + publicCode;
    String widgetSnippet =
        "<script src=\"%s\"></script>\n<div id=\"agentx-chatbot\"></div>\n<script>window.AgentXChatbot.init({ target: '#agentx-chatbot', bot: '%s' });</script>"
            .formatted(widgetScriptUrl, publicCode);

    return new DeploymentOverview(
        chatbot.getId(),
        chatbot.getTenantId(),
        publicCode,
        widgetScriptUrl,
        chatPageUrl,
        widgetSnippet,
        whitelistDomainRepository.countByChatbotId(chatbot.getId()),
        accessLogRepository.findTop10ByChatbotIdOrderByCreatedAtDescIdDesc(chatbot.getId()).stream()
            .map(this::toSummary)
            .toList());
  }

  private WhitelistDomainSummary toSummary(DeploymentWhitelistDomain entity) {
    return new WhitelistDomainSummary(entity.getId(), entity.getDomain(), entity.getCreatedAt());
  }

  private DeploymentAccessSummary toSummary(DeploymentAccessLog entity) {
    return new DeploymentAccessSummary(
        entity.getId(),
        entity.getConversationId(),
        entity.getEntryType(),
        entity.getDomainName(),
        entity.getIpAddress(),
        entity.getUserAgent(),
        entity.getCreatedAt());
  }

  private Chatbot loadChatbot(CurrentUser actor, Long chatbotId) {
    Chatbot chatbot = chatbotRepository.findById(chatbotId).orElseThrow();
    if (!actor.isSuperAdmin() && !chatbot.getTenantId().equals(actor.tenantId())) {
      throw new IllegalArgumentException("CHATBOT_NOT_FOUND");
    }
    return chatbot;
  }

  private String normalizeDomain(String domain) {
    String value = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      throw new IllegalArgumentException("DOMAIN_REQUIRED");
    }

    if (value.startsWith("http://") || value.startsWith("https://")) {
      value = URI.create(value).getHost();
    }

    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("DOMAIN_REQUIRED");
    }

    if (value.contains("/") || value.contains(" ")) {
      throw new IllegalArgumentException("DOMAIN_INVALID");
    }

    return value;
  }

  private String normalizeDomainForLog(String domain) {
    String value = blankToNull(domain);
    if (value == null) {
      return null;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    try {
      if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
        String host = URI.create(normalized).getHost();
        return blankToNull(host);
      }
    } catch (IllegalArgumentException ignored) {
      return normalized;
    }

    int slashIndex = normalized.indexOf('/');
    if (slashIndex >= 0) {
      normalized = normalized.substring(0, slashIndex);
    }

    return blankToNull(normalized);
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return value.trim();
  }

  public record DeploymentOverview(
      Long chatbotId,
      Long tenantId,
      String chatbotPublicCode,
      String widgetScriptUrl,
      String chatPageUrl,
      String widgetSnippet,
      long whitelistCount,
      List<DeploymentAccessSummary> recentAccesses) {}

  public record WhitelistDomainSummary(Long id, String domain, Instant createdAt) {}

  public record DeploymentAccessSummary(
      Long id,
      Long conversationId,
      String entryType,
      String domain,
      String ipAddress,
      String userAgent,
      Instant createdAt) {}
}