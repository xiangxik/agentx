package com.agentx.backend.tenant.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.auth.application.BootstrapDataInitializer;
import com.agentx.backend.auth.domain.AppUser;
import com.agentx.backend.auth.domain.AppUserRepository;
import com.agentx.backend.auth.domain.Role;
import com.agentx.backend.auth.domain.RoleRepository;
import com.agentx.backend.auth.domain.UserStatus;
import com.agentx.backend.auth.domain.UserType;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.tenant.domain.Tenant;
import com.agentx.backend.tenant.domain.TenantRepository;
import com.agentx.backend.tenant.domain.TenantStatus;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

  private final TenantRepository tenantRepository;
  private final AppUserRepository appUserRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;
  private final BootstrapDataInitializer bootstrapDataInitializer;

  public TenantService(
      TenantRepository tenantRepository,
      AppUserRepository appUserRepository,
      RoleRepository roleRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService,
      BootstrapDataInitializer bootstrapDataInitializer) {
    this.tenantRepository = tenantRepository;
    this.appUserRepository = appUserRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
    this.bootstrapDataInitializer = bootstrapDataInitializer;
  }

  @Transactional
  public TenantSummary create(CurrentUser actor, CreateTenantRequest request) {
    Tenant tenant = new Tenant();
    tenant.setCode(request.code());
    tenant.setName(request.name());
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setContactName(request.contactName());
    tenant.setContactEmail(request.contactEmail());
    tenant.setNotes(request.notes());
    Tenant savedTenant = tenantRepository.save(tenant);

    Role tenantAdminRole =
        roleRepository
            .findByCode("TENANT_ADMIN")
            .orElseGet(() -> bootstrapDataInitializer.ensureRole("TENANT_ADMIN", "租户管理员"));
    AppUser admin = new AppUser();
    admin.setTenantId(savedTenant.getId());
    admin.setEmail(request.adminEmail());
    admin.setDisplayName(request.adminDisplayName());
    admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
    admin.setUserType(UserType.TENANT_USER);
    admin.setStatus(UserStatus.ACTIVE);
    admin.getRoles().add(tenantAdminRole);
    appUserRepository.save(admin);

    auditLogService.record(
        savedTenant.getId(),
        actor.userId(),
        "TENANT_CREATED",
        "TENANT",
        String.valueOf(savedTenant.getId()),
        "SUCCESS",
        "LOW",
        Map.of("code", savedTenant.getCode(), "adminEmail", admin.getEmail()));

    return new TenantSummary(
        savedTenant.getId(),
        savedTenant.getCode(),
        savedTenant.getName(),
        savedTenant.getStatus().name(),
        savedTenant.getContactName(),
        savedTenant.getContactEmail());
  }

  @Transactional(readOnly = true)
  public List<TenantSummary> list() {
    return tenantRepository.findAll().stream()
        .map(
            tenant ->
                new TenantSummary(
                    tenant.getId(),
                    tenant.getCode(),
                    tenant.getName(),
                    tenant.getStatus().name(),
                    tenant.getContactName(),
                    tenant.getContactEmail()))
        .toList();
  }

    @Transactional(readOnly = true)
    public TenantDetail get(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        AppUser admin = appUserRepository.findFirstByTenantId(tenantId).orElse(null);

        return new TenantDetail(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus().name(),
                tenant.getContactName(),
                tenant.getContactEmail(),
                tenant.getNotes(),
                admin == null ? null : new TenantAdminSummary(admin.getId(), admin.getEmail(), admin.getDisplayName()));
    }

    @Transactional
    public TenantDetail update(CurrentUser actor, Long tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setName(request.name());
        tenant.setContactName(request.contactName());
        tenant.setContactEmail(request.contactEmail());
        tenant.setNotes(request.notes());

        auditLogService.record(
                tenant.getId(),
                actor.userId(),
                "TENANT_UPDATED",
                "TENANT",
                String.valueOf(tenantId),
                "SUCCESS",
                "LOW",
                Map.of("name", tenant.getName(), "contactEmail", String.valueOf(tenant.getContactEmail())));

        AppUser admin = appUserRepository.findFirstByTenantId(tenantId).orElse(null);

        return new TenantDetail(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus().name(),
                tenant.getContactName(),
                tenant.getContactEmail(),
                tenant.getNotes(),
                admin == null ? null : new TenantAdminSummary(admin.getId(), admin.getEmail(), admin.getDisplayName()));
    }

  @Transactional
  public TenantSummary updateStatus(CurrentUser actor, Long tenantId, TenantStatus status) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    tenant.setStatus(status);
    auditLogService.record(
        tenant.getId(),
        actor.userId(),
        "TENANT_STATUS_UPDATED",
        "TENANT",
        String.valueOf(tenantId),
        "SUCCESS",
        "MEDIUM",
        Map.of("status", status.name()));
    return new TenantSummary(
        tenant.getId(),
        tenant.getCode(),
        tenant.getName(),
        tenant.getStatus().name(),
        tenant.getContactName(),
        tenant.getContactEmail());
  }

  public record CreateTenantRequest(
      String code,
      String name,
      String contactName,
      String contactEmail,
      String notes,
      String adminEmail,
      String adminDisplayName,
      String adminPassword) {}

  public record UpdateTenantRequest(
      String name, String contactName, String contactEmail, String notes) {}

  public record TenantSummary(
      Long id, String code, String name, String status, String contactName, String contactEmail) {}

  public record TenantAdminSummary(Long id, String email, String displayName) {}

  public record TenantDetail(
      Long id,
      String code,
      String name,
      String status,
      String contactName,
      String contactEmail,
      String notes,
      TenantAdminSummary admin) {}
}
