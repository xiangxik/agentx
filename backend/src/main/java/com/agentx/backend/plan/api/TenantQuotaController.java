package com.agentx.backend.plan.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.plan.application.PlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quota")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class TenantQuotaController {

  private final PlanService planService;

  public TenantQuotaController(PlanService planService) {
    this.planService = planService;
  }

  @GetMapping
  public PlanService.TenantQuotaOverview get(@RequestParam(required = false) Long tenantId) {
    return planService.getTenantQuotaOverview(SecurityUtils.currentUser(), tenantId);
  }
}