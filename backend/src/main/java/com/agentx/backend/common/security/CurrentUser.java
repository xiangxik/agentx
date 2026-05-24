package com.agentx.backend.common.security;

import java.util.Set;

public record CurrentUser(Long userId, Long tenantId, String email, Set<String> roles) {

  public boolean isSuperAdmin() {
    return roles.contains("SUPER_ADMIN");
  }
}
