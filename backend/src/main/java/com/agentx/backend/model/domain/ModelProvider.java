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
@Table(name = "model_provider")
public class ModelProvider {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "provider_code", nullable = false, unique = true)
  private String providerCode;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "api_endpoint")
  private String apiEndpoint;

  @Column(name = "api_key_hint")
  private String apiKeyHint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelProviderStatus status = ModelProviderStatus.ACTIVE;

  @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
  private String metadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() {
    return id;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public void setProviderCode(String providerCode) {
    this.providerCode = providerCode;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public void setApiEndpoint(String apiEndpoint) {
    this.apiEndpoint = apiEndpoint;
  }

  public String getApiKeyHint() {
    return apiKeyHint;
  }

  public void setApiKeyHint(String apiKeyHint) {
    this.apiKeyHint = apiKeyHint;
  }

  public ModelProviderStatus getStatus() {
    return status;
  }

  public void setStatus(ModelProviderStatus status) {
    this.status = status;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}