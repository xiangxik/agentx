package com.agentx.backend.auth.application;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class TenantUserPrincipal extends User {

  private final Long userId;
  private final Long tenantId;

  public TenantUserPrincipal(
      Long userId,
      Long tenantId,
      String username,
      String password,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
    this.userId = userId;
    this.tenantId = tenantId;
  }

  public Long userId() {
    return userId;
  }

  public Long tenantId() {
    return tenantId;
  }
}
