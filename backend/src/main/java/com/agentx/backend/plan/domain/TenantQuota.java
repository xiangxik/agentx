package com.agentx.backend.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_quota")
public class TenantQuota {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  private Long tenantId;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "overrides_json", nullable = false)
  private String overridesJson;

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Long getPlanId() {
    return planId;
  }

  public void setPlanId(Long planId) {
    this.planId = planId;
  }

  public String getOverridesJson() {
    return overridesJson;
  }

  public void setOverridesJson(String overridesJson) {
    this.overridesJson = overridesJson;
  }
}
