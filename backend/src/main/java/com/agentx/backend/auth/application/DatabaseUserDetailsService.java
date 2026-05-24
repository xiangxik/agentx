package com.agentx.backend.auth.application;

import com.agentx.backend.auth.domain.AppUser;
import com.agentx.backend.auth.domain.AppUserRepository;
import java.util.stream.Collectors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final AppUserRepository appUserRepository;

  public DatabaseUserDetailsService(AppUserRepository appUserRepository) {
    this.appUserRepository = appUserRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AppUser user =
        appUserRepository
            .findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    return new TenantUserPrincipal(
        user.getId(),
        user.getTenantId(),
        user.getEmail(),
        user.getPasswordHash(),
        user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
            .collect(Collectors.toSet()));
  }
}
