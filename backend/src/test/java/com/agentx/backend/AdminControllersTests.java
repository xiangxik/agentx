package com.agentx.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminControllersTests {

  @Autowired private MockMvc mockMvc;

  @Test
  void superAdminCanCreateTenant() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/tenants")
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "code":"tenant-a",
                                  "name":"Tenant A",
                                  "contactName":"Alice",
                                  "contactEmail":"alice@tenant.test",
                                  "notes":"seed tenant",
                                  "adminEmail":"owner@tenant.test",
                                  "adminDisplayName":"Owner",
                                  "adminPassword":"Tenant123!"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("tenant-a"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void superAdminCanCreatePlanAndAssignQuota() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/plans")
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "code":"starter",
                                  "name":"Starter",
                                  "limits":{"chatbots":3,"messages":1000,"tokens":20000}
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("starter"));
  }

  @Test
  void nonSuperAdminCannotListTenants() throws Exception {
    mockMvc
        .perform(get("/api/admin/tenants").with(user("editor@example.com").roles("TENANT_ADMIN")))
        .andExpect(status().isForbidden());
  }
}
