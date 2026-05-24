package com.agentx.backend.plan.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.plan.application.PlanService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/plans")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlanAdminController {

  private final PlanService planService;

  public PlanAdminController(PlanService planService) {
    this.planService = planService;
  }

  @GetMapping
  public List<PlanService.PlanSummary> list() {
    return planService.list();
  }

  @PostMapping
  public PlanService.PlanSummary create(@RequestBody CreatePlanRequest request) {
    return planService.create(
        SecurityUtils.currentUser(),
        new PlanService.CreatePlanRequest(request.code(), request.name(), request.limits()));
  }

  @PostMapping("/assignments")
  public PlanService.TenantQuotaSummary assign(@RequestBody AssignPlanRequest request) {
    return planService.assign(
        SecurityUtils.currentUser(),
        new PlanService.AssignTenantQuotaRequest(
            request.tenantId(), request.planId(), request.overrides()));
  }

  @GetMapping("/assignments/{tenantId}")
  public PlanService.TenantQuotaSummary getTenantQuota(@PathVariable Long tenantId) {
    return planService.getTenantQuota(tenantId);
  }

  public record CreatePlanRequest(
      @NotBlank String code, @NotBlank String name, Map<String, Long> limits) {}

  public record AssignPlanRequest(Long tenantId, Long planId, Map<String, Long> overrides) {}
}
