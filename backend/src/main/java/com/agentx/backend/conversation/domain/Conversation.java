package com.agentx.backend.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversation")
public class Conversation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id", nullable = false)
  private Long chatbotId;

  @Column(name = "anonymous_visitor_id", nullable = false)
  private String anonymousVisitorId;

  @Column(name = "entry_type", nullable = false)
  private String entryType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ConversationStatus status = ConversationStatus.ACTIVE;

  @Column(name = "metadata_json", nullable = false)
  private String metadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

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

  public String getAnonymousVisitorId() {
    return anonymousVisitorId;
  }

  public void setAnonymousVisitorId(String anonymousVisitorId) {
    this.anonymousVisitorId = anonymousVisitorId;
  }

  public String getEntryType() {
    return entryType;
  }

  public void setEntryType(String entryType) {
    this.entryType = entryType;
  }

  public ConversationStatus getStatus() {
    return status;
  }

  public void setStatus(ConversationStatus status) {
    this.status = status;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
