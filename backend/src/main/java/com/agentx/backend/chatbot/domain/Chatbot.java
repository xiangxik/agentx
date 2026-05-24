package com.agentx.backend.chatbot.domain;

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
@Table(name = "chatbot")
public class Chatbot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false)
  private String name;

  @Column private String description;

  @Column(nullable = false)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ChatbotStatus status = ChatbotStatus.DRAFT;

  @Column(name = "public_code", nullable = false, unique = true)
  private String publicCode;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public ChatbotStatus getStatus() {
    return status;
  }

  public void setStatus(ChatbotStatus status) {
    this.status = status;
  }

  public String getPublicCode() {
    return publicCode;
  }

  public void setPublicCode(String publicCode) {
    this.publicCode = publicCode;
  }
}
