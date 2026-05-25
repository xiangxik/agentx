package com.agentx.backend.knowledge.domain;

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
@Table(name = "knowledge_source")
public class KnowledgeSource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id", nullable = false)
  private Long chatbotId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private KnowledgeSourceType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private KnowledgeSourceStatus status = KnowledgeSourceStatus.UPLOADED;

  @Column(name = "source_name", nullable = false)
  private String sourceName;

  @Column(name = "source_uri")
  private String sourceUri;

  @Column(name = "metadata_json", nullable = false)
  private String metadataJson;

  @Column(name = "failure_reason")
  private String failureReason;

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

  public KnowledgeSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(KnowledgeSourceType sourceType) {
    this.sourceType = sourceType;
  }

  public KnowledgeSourceStatus getStatus() {
    return status;
  }

  public void setStatus(KnowledgeSourceStatus status) {
    this.status = status;
  }

  public String getSourceName() {
    return sourceName;
  }

  public void setSourceName(String sourceName) {
    this.sourceName = sourceName;
  }

  public String getSourceUri() {
    return sourceUri;
  }

  public void setSourceUri(String sourceUri) {
    this.sourceUri = sourceUri;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}