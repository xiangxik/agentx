package com.agentx.backend.auth.application;

import com.agentx.backend.auth.domain.AppUser;
import com.agentx.backend.auth.domain.AppUserRepository;
import com.agentx.backend.auth.domain.Role;
import com.agentx.backend.auth.domain.RoleRepository;
import com.agentx.backend.auth.domain.UserStatus;
import com.agentx.backend.auth.domain.UserType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapDataInitializer implements CommandLineRunner {

  private final RoleRepository roleRepository;
  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final BootstrapProperties bootstrapProperties;

  public BootstrapDataInitializer(
      RoleRepository roleRepository,
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      BootstrapProperties bootstrapProperties) {
    this.roleRepository = roleRepository;
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.bootstrapProperties = bootstrapProperties;
  }

  @Override
  public void run(String... args) {
    Role superAdminRole = ensureRole("SUPER_ADMIN", "超级管理员");
    ensureRole("TENANT_ADMIN", "租户管理员");

    appUserRepository
        .findByEmail(bootstrapProperties.email())
        .orElseGet(
            () -> {
              AppUser user = new AppUser();
              user.setEmail(bootstrapProperties.email());
              user.setDisplayName(bootstrapProperties.displayName());
              user.setPasswordHash(passwordEncoder.encode(bootstrapProperties.password()));
              user.setUserType(UserType.SUPER_ADMIN);
              user.setStatus(UserStatus.ACTIVE);
              user.getRoles().add(superAdminRole);
              return appUserRepository.save(user);
            });
  }

  public Role ensureRole(String code, String name) {
    return roleRepository
        .findByCode(code)
        .orElseGet(
            () -> {
              Role role = new Role();
              role.setCode(code);
              role.setName(name);
              return roleRepository.save(role);
            });
  }
}
