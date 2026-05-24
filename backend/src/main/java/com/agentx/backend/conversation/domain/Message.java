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
@Table(name = "message")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MessageRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MessageStatus status = MessageStatus.SENT;

  @Column(nullable = false)
  private String content;

  @Column(name = "metadata_json", nullable = false)
  private String metadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public void setConversationId(Long conversationId) {
    this.conversationId = conversationId;
  }

  public MessageRole getRole() {
    return role;
  }

  public void setRole(MessageRole role) {
    this.role = role;
  }

  public MessageStatus getStatus() {
    return status;
  }

  public void setStatus(MessageStatus status) {
    this.status = status;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
