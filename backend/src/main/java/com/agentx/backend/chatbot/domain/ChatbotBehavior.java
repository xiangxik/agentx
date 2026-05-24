package com.agentx.backend.chatbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chatbot_behavior")
public class ChatbotBehavior {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id", nullable = false, unique = true)
  private Long chatbotId;

  @Column(name = "fallback_message", nullable = false)
  private String fallbackMessage;

  @Column(name = "config_json", nullable = false)
  private String configJson;

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Long getChatbotId() {
    return chatbotId;
  }

  public void setChatbotId(Long chatbotId) {
    this.chatbotId = chatbotId;
  }

  public String getFallbackMessage() {
    return fallbackMessage;
  }

  public void setFallbackMessage(String fallbackMessage) {
    this.fallbackMessage = fallbackMessage;
  }

  public String getConfigJson() {
    return configJson;
  }

  public void setConfigJson(String configJson) {
    this.configJson = configJson;
  }
}
