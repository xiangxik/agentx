package com.agentx.backend.model.domain;

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
@Table(name = "model_call_log")
public class ModelCallLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id")
  private Long chatbotId;

  @Column(name = "conversation_id")
  private Long conversationId;

  @Column(name = "assistant_message_id")
  private Long assistantMessageId;

  @Column(name = "provider_code", nullable = false)
  private String providerCode;

  @Column(name = "model_code", nullable = false)
  private String modelCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelCallStatus status;

  @Column(name = "prompt_tokens", nullable = false)
  private int promptTokens;

  @Column(name = "completion_tokens", nullable = false)
  private int completionTokens;

  @Column(name = "total_tokens", nullable = false)
  private int totalTokens;

  @Column(name = "estimated_cost", nullable = false)
  private double estimatedCost;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "latency_ms", nullable = false)
  private long latencyMs;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
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

  public Long getChatbotId() {
    return chatbotId;
  }

  public void setChatbotId(Long chatbotId) {
    this.chatbotId = chatbotId;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public void setConversationId(Long conversationId) {
    this.conversationId = conversationId;
  }

  public Long getAssistantMessageId() {
    return assistantMessageId;
  }

  public void setAssistantMessageId(Long assistantMessageId) {
    this.assistantMessageId = assistantMessageId;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public void setProviderCode(String providerCode) {
    this.providerCode = providerCode;
  }

  public String getModelCode() {
    return modelCode;
  }

  public void setModelCode(String modelCode) {
    this.modelCode = modelCode;
  }

  public ModelPurpose getPurpose() {
    return purpose;
  }

  public void setPurpose(ModelPurpose purpose) {
    this.purpose = purpose;
  }

  public ModelCallStatus getStatus() {
    return status;
  }

  public void setStatus(ModelCallStatus status) {
    this.status = status;
  }

  public int getPromptTokens() {
    return promptTokens;
  }

  public void setPromptTokens(int promptTokens) {
    this.promptTokens = promptTokens;
  }

  public int getCompletionTokens() {
    return completionTokens;
  }

  public void setCompletionTokens(int completionTokens) {
    this.completionTokens = completionTokens;
  }

  public int getTotalTokens() {
    return totalTokens;
  }

  public void setTotalTokens(int totalTokens) {
    this.totalTokens = totalTokens;
  }

  public double getEstimatedCost() {
    return estimatedCost;
  }

  public void setEstimatedCost(double estimatedCost) {
    this.estimatedCost = estimatedCost;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public long getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(long latencyMs) {
    this.latencyMs = latencyMs;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}