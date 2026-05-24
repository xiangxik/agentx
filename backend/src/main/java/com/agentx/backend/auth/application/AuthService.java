package com.agentx.backend.auth.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.auth.domain.AppUser;
import com.agentx.backend.auth.domain.AppUserRepository;
import com.agentx.backend.auth.domain.UserStatus;
import com.agentx.backend.auth.domain.UserType;
import com.agentx.backend.tenant.domain.TenantRepository;
import com.agentx.backend.tenant.domain.TenantStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;

  private final AppUserRepository appUserRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditLogService auditLogService;

  public AuthService(
      AppUserRepository appUserRepository,
      TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder,
      AuditLogService auditLogService) {
    this.appUserRepository = appUserRepository;
    this.tenantRepository = tenantRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    AppUser user =
        appUserRepository
            .findByEmail(request.email())
            .orElseThrow(() -> invalidCredentials(request.email()));

    if (user.getStatus() == UserStatus.DISABLED) {
      throw new AuthException("ACCOUNT_DISABLED");
    }

    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
      throw new AuthException("ACCOUNT_LOCKED");
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      int attempts = user.getFailedLoginCount() + 1;
      user.setFailedLoginCount(attempts);
      if (attempts >= MAX_FAILED_ATTEMPTS) {
        user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
      }
      auditLogService.record(
          user.getTenantId(),
          user.getId(),
          "AUTH_LOGIN_FAILED",
          "USER",
          String.valueOf(user.getId()),
          "FAILED",
          "MEDIUM",
          Map.of("email", request.email()));
      throw invalidCredentials(request.email());
    }

    if (user.getUserType() == UserType.TENANT_USER && user.getTenantId() != null) {
      boolean tenantActive =
          tenantRepository
              .findById(user.getTenantId())
              .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
              .orElse(false);
      if (!tenantActive) {
        throw new AuthException("TENANT_DISABLED");
      }
    }

    user.setFailedLoginCount(0);
    user.setLockedUntil(null);
    auditLogService.record(
        user.getTenantId(),
        user.getId(),
        "AUTH_LOGIN",
        "USER",
        String.valueOf(user.getId()),
        "SUCCESS",
        "LOW",
        Map.of("email", request.email()));

    return new LoginResponse(
        user.getId(),
        user.getTenantId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRoles().stream().map(role -> role.getCode()).collect(Collectors.toSet()),
        issueToken(user));
  }

  private String issueToken(AppUser user) {
    return "%s:%s:%d"
        .formatted(user.getUserType().name(), user.getEmail(), Instant.now().getEpochSecond());
  }

  private AuthException invalidCredentials(String email) {
    return new AuthException("INVALID_CREDENTIALS");
  }

  public record LoginRequest(String email, String password) {}

  public record LoginResponse(
      Long userId,
      Long tenantId,
      String email,
      String displayName,
      java.util.Set<String> roles,
      String accessToken) {}
}
