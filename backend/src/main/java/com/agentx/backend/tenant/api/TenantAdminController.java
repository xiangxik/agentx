package com.agentx.backend.tenant.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.tenant.application.TenantService;
import com.agentx.backend.tenant.domain.TenantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminController {

  private final TenantService tenantService;

  public TenantAdminController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @GetMapping
  public List<TenantService.TenantSummary> list() {
    return tenantService.list();
  }

  @GetMapping("/{tenantId}")
  public TenantService.TenantDetail get(@PathVariable Long tenantId) {
    return tenantService.get(tenantId);
  }

  @PostMapping
  public TenantService.TenantSummary create(@RequestBody CreateTenantRequest request) {
    return tenantService.create(
        SecurityUtils.currentUser(),
        new TenantService.CreateTenantRequest(
            request.code(),
            request.name(),
            request.contactName(),
            request.contactEmail(),
            request.notes(),
            request.adminEmail(),
            request.adminDisplayName(),
            request.adminPassword()));
  }

  @PatchMapping("/{tenantId}")
  public TenantService.TenantDetail update(
      @PathVariable Long tenantId, @RequestBody UpdateTenantRequest request) {
    return tenantService.update(
        SecurityUtils.currentUser(),
        tenantId,
        new TenantService.UpdateTenantRequest(
            request.name(), request.contactName(), request.contactEmail(), request.notes()));
  }

  @PatchMapping("/{tenantId}/status")
  public TenantService.TenantSummary updateStatus(
      @PathVariable Long tenantId, @RequestBody UpdateStatusRequest request) {
    return tenantService.updateStatus(
        SecurityUtils.currentUser(), tenantId, TenantStatus.valueOf(request.status()));
  }

  public record CreateTenantRequest(
      @NotBlank String code,
      @NotBlank String name,
      String contactName,
      @Email String contactEmail,
      String notes,
      @Email String adminEmail,
      @NotBlank String adminDisplayName,
      @NotBlank String adminPassword) {}

  public record UpdateTenantRequest(
      @NotBlank String name, String contactName, @Email String contactEmail, String notes) {}

  public record UpdateStatusRequest(@NotBlank String status) {}
}
