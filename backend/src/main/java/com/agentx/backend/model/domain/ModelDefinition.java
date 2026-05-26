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
@Table(name = "model_definition")
public class ModelDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "provider_id", nullable = false)
  private Long providerId;

  @Column(name = "model_code", nullable = false)
  private String modelCode;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ModelDefinitionStatus status = ModelDefinitionStatus.ACTIVE;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "input_price_per_1k")
  private Double inputPricePer1k;

  @Column(name = "output_price_per_1k")
  private Double outputPricePer1k;

  @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
  private String configJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getProviderId() {
    return providerId;
  }

  public void setProviderId(Long providerId) {
    this.providerId = providerId;
  }

  public String getModelCode() {
    return modelCode;
  }

  public void setModelCode(String modelCode) {
    this.modelCode = modelCode;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public ModelPurpose getPurpose() {
    return purpose;
  }

  public void setPurpose(ModelPurpose purpose) {
    this.purpose = purpose;
  }

  public ModelDefinitionStatus getStatus() {
    return status;
  }

  public void setStatus(ModelDefinitionStatus status) {
    this.status = status;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean aDefault) {
    isDefault = aDefault;
  }

  public Double getInputPricePer1k() {
    return inputPricePer1k;
  }

  public void setInputPricePer1k(Double inputPricePer1k) {
    this.inputPricePer1k = inputPricePer1k;
  }

  public Double getOutputPricePer1k() {
    return outputPricePer1k;
  }

  public void setOutputPricePer1k(Double outputPricePer1k) {
    this.outputPricePer1k = outputPricePer1k;
  }

  public String getConfigJson() {
    return configJson;
  }

  public void setConfigJson(String configJson) {
    this.configJson = configJson;
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