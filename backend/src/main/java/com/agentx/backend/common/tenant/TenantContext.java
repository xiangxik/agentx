package com.agentx.backend.common.tenant;

import java.util.Optional;

public final class TenantContext {

  private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

  private TenantContext() {}

  public static void setTenantId(Long tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  public static Optional<Long> getTenantId() {
    return Optional.ofNullable(CURRENT_TENANT.get());
  }

  public static void clear() {
    CURRENT_TENANT.remove();
  }
}
