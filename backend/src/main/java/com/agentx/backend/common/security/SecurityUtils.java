package com.agentx.backend.common.security;

import com.agentx.backend.auth.application.TenantUserPrincipal;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {}

  public static CurrentUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null
        || !(authentication.getPrincipal() instanceof TenantUserPrincipal principal)) {
      return new CurrentUser(null, null, "anonymous", Set.of());
    }

    Set<String> roles =
        principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replace("ROLE_", ""))
            .collect(Collectors.toSet());

    return new CurrentUser(
        principal.userId(), principal.tenantId(), principal.getUsername(), roles);
  }
}
