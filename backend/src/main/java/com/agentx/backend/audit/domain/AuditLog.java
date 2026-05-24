package com.agentx.backend.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "action_type", nullable = false)
  private String actionType;

  @Column(name = "target_type", nullable = false)
  private String targetType;

  @Column(name = "target_id")
  private String targetId;

  @Column(name = "result", nullable = false)
  private String result;

  @Column(name = "risk_level", nullable = false)
  private String riskLevel;

  @Column(name = "context_json")
  private String contextJson;

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

  public Long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(Long actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(String riskLevel) {
    this.riskLevel = riskLevel;
  }

  public String getContextJson() {
    return contextJson;
  }

  public void setContextJson(String contextJson) {
    this.contextJson = contextJson;
  }
}
