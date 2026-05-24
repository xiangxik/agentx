package com.agentx.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.domain.AppUser;
import com.agentx.backend.auth.domain.AppUserRepository;
import com.agentx.backend.auth.domain.Role;
import com.agentx.backend.auth.domain.RoleRepository;
import com.agentx.backend.auth.domain.UserStatus;
import com.agentx.backend.auth.domain.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private RoleRepository roleRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    appUserRepository.deleteAll();
    roleRepository.deleteAll();

    Role role = new Role();
    role.setCode("SUPER_ADMIN");
    role.setName("超级管理员");
    Role savedRole = roleRepository.save(role);

    AppUser user = new AppUser();
    user.setEmail("admin@example.com");
    user.setDisplayName("Admin");
    user.setPasswordHash(passwordEncoder.encode("Admin123!"));
    user.setUserType(UserType.SUPER_ADMIN);
    user.setStatus(UserStatus.ACTIVE);
    user.getRoles().add(savedRole);
    appUserRepository.save(user);
  }

  @Test
  void loginReturnsTokenForValidCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/public/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"admin@example.com","password":"Admin123!"}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("admin@example.com"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));
  }

  @Test
  void loginRejectsInvalidCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/public/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"email":"admin@example.com","password":"wrong"}
                                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }
}
