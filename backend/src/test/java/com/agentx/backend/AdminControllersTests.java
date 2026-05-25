package com.agentx.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.application.BootstrapDataInitializer;
import com.agentx.backend.auth.application.DatabaseUserDetailsService;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminControllersTests {

  @Autowired private MockMvc mockMvc;
  @Autowired private DatabaseUserDetailsService userDetailsService;
  @Autowired private KnowledgeSourceRepository knowledgeSourceRepository;

  private UserDetails authUser(String email) {
    return userDetailsService.loadUserByUsername(email);
  }

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
    String planResponse =
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
                    .andExpect(jsonPath("$.code").value("starter"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

                  long planId = JsonTestUtils.readLong(planResponse, "id");

                  String tenantResponse =
                    mockMvc
                      .perform(
                        post("/api/admin/tenants")
                          .with(user("admin@example.com").roles("SUPER_ADMIN"))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                            """
                            {
                              "code":"tenant-plan",
                              "name":"Tenant Plan",
                              "contactName":"Penny",
                              "contactEmail":"penny@tenant.test",
                              "notes":"quota view",
                              "adminEmail":"owner-plan@tenant.test",
                              "adminDisplayName":"Owner Plan",
                              "adminPassword":"Tenant123!"
                            }
                            """))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString();

                  long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

                  mockMvc
                    .perform(
                      post("/api/admin/plans/assignments")
                        .with(user("admin@example.com").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                          """
                          {
                            "tenantId":%d,
                            "planId":%d,
                            "overrides":{"messages":1500,"chatbots":1}
                          }
                          """.formatted(tenantId, planId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value(tenantId));

                  mockMvc
                    .perform(get("/api/admin/quota").with(user(authUser("owner-plan@tenant.test"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planId").value(planId))
                    .andExpect(jsonPath("$.effectiveLimits.messages").value(1500));

                  String firstChatbotResponse =
                    mockMvc
                    .perform(
                      post("/api/admin/chatbots")
                        .with(user(authUser("owner-plan@tenant.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Quota First Bot",
                            "description":"first",
                            "language":"zh-CN",
                            "status":"DRAFT"
                          }
                          """.formatted(tenantId)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

                  long firstChatbotId = JsonTestUtils.readLong(firstChatbotResponse, "id");

                  mockMvc
                    .perform(
                      post("/api/admin/chatbots")
                        .with(user(authUser("owner-plan@tenant.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Quota Second Bot",
                            "description":"second",
                            "language":"zh-CN",
                            "status":"DRAFT"
                          }
                          """.formatted(tenantId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHATBOTS_LIMIT_REACHED"));

                  mockMvc
                    .perform(
                      delete("/api/admin/chatbots/{chatbotId}", firstChatbotId)
                        .with(user(authUser("owner-plan@tenant.test"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DELETED"));

                  mockMvc
                    .perform(
                      post("/api/admin/chatbots")
                        .with(user(authUser("owner-plan@tenant.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Quota Third Bot",
                            "description":"third",
                            "language":"zh-CN",
                            "status":"DRAFT"
                          }
                          """.formatted(tenantId)))
                    .andExpect(status().isOk());

  }

  @Test
  void superAdminCanUpdateAndDisablePlan() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/admin/plans")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"growth",
                          "name":"Growth",
                          "limits":{"chatbots":10,"messages":5000,"tokens":100000}
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long planId = JsonTestUtils.readLong(response, "id");

    mockMvc
        .perform(
            patch("/api/admin/plans/{planId}", planId)
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"Growth Plus",
                      "limits":{"chatbots":12,"messages":8000,"tokens":120000}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Growth Plus"))
        .andExpect(jsonPath("$.limits.chatbots").value(12));

    mockMvc
        .perform(
            patch("/api/admin/plans/{planId}/status", planId)
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "status":"DISABLED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));
  }

  @Test
  void nonSuperAdminCannotListTenants() throws Exception {
    mockMvc
        .perform(get("/api/admin/tenants").with(user("editor@example.com").roles("TENANT_ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void bearerTokenCanAccessProtectedTenantEndpoint() throws Exception {
    String loginResponse =
        mockMvc
            .perform(
                post("/api/public/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"admin@agentx.local",
                          "password":"Admin123!"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String accessToken = JsonTestUtils.readText(loginResponse, "accessToken");

    mockMvc
        .perform(get("/api/admin/tenants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
  }

      @Test
      void tenantAdminCanUploadKnowledgeFileWithinQuota() throws Exception {
      String planResponse =
        mockMvc
          .perform(
            post("/api/admin/plans")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"knowledge-starter",
                  "name":"Knowledge Starter",
                  "limits":{"chatbots":3,"files":1,"storageMb":1,"messages":1000}
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long planId = JsonTestUtils.readLong(planResponse, "id");

      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-knowledge",
                  "name":"Tenant Knowledge",
                  "contactName":"Kara",
                  "contactEmail":"kara@tenant.test",
                  "notes":"knowledge upload",
                  "adminEmail":"owner-knowledge@tenant.test",
                  "adminDisplayName":"Owner Knowledge",
                  "adminPassword":"Tenant123!"
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/plans/assignments")
            .with(user("admin@example.com").roles("SUPER_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "tenantId":%d,
                "planId":%d,
                "overrides":{}
              }
              """.formatted(tenantId, planId)))
        .andExpect(status().isOk());

      String chatbotResponse =
        mockMvc
          .perform(
            post("/api/admin/chatbots")
              .with(user(authUser("owner-knowledge@tenant.test")))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Knowledge Bot",
                  "description":"kb",
                  "language":"zh-CN",
                  "status":"ACTIVE"
                }
                """.formatted(tenantId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

      MockMultipartFile firstFile =
        new MockMultipartFile(
          "file", "guide.txt", "text/plain", "hello knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      mockMvc
        .perform(
          multipart("/api/admin/knowledge-sources/upload")
            .file(firstFile)
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-knowledge@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceName").value("guide.txt"))
        .andExpect(jsonPath("$.status").value("UPLOADED"));

      MockMultipartFile secondFile =
        new MockMultipartFile(
          "file", "extra.txt", "text/plain", "second file".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      mockMvc
        .perform(
          multipart("/api/admin/knowledge-sources/upload")
            .file(secondFile)
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-knowledge@tenant.test"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FILES_LIMIT_REACHED"));

      mockMvc
        .perform(
          get("/api/admin/knowledge-sources")
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-knowledge@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sourceName").value("guide.txt"));
      }

      @Test
      void tenantAdminCannotUploadUnsupportedOrOversizedKnowledgeFile() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-invalid-file",
                  "name":"Tenant Invalid File",
                  "contactName":"Ivy",
                  "contactEmail":"ivy@tenant.test",
                  "notes":"invalid file upload",
                  "adminEmail":"owner-invalid-file@tenant.test",
                  "adminDisplayName":"Owner Invalid File",
                  "adminPassword":"Tenant123!"
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

      String chatbotResponse =
        mockMvc
          .perform(
            post("/api/admin/chatbots")
              .with(user(authUser("owner-invalid-file@tenant.test")))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Invalid File Bot",
                  "description":"invalid file",
                  "language":"zh-CN",
                  "status":"ACTIVE"
                }
                """.formatted(tenantId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

      MockMultipartFile unsupportedFile =
        new MockMultipartFile(
          "file", "malware.exe", "application/octet-stream", "boom".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      mockMvc
        .perform(
          multipart("/api/admin/knowledge-sources/upload")
            .file(unsupportedFile)
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-invalid-file@tenant.test"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_SUPPORTED"));

      MockMultipartFile oversizedFile =
        new MockMultipartFile(
          "file", "large.txt", "text/plain", new byte[11 * 1024 * 1024]);

      mockMvc
        .perform(
          multipart("/api/admin/knowledge-sources/upload")
            .file(oversizedFile)
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-invalid-file@tenant.test"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_SIZE_LIMIT_EXCEEDED"));
      }

      @Test
      void tenantAdminCanCreateWebKnowledgeSourceAndRejectInvalidUrl() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-web-knowledge",
                  "name":"Tenant Web Knowledge",
                  "contactName":"Wendy",
                  "contactEmail":"wendy@tenant.test",
                  "notes":"knowledge web source",
                  "adminEmail":"owner-web-knowledge@tenant.test",
                  "adminDisplayName":"Owner Web Knowledge",
                  "adminPassword":"Tenant123!"
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

      String chatbotResponse =
        mockMvc
          .perform(
            post("/api/admin/chatbots")
              .with(user(authUser("owner-web-knowledge@tenant.test")))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Web Knowledge Bot",
                  "description":"web kb",
                  "language":"zh-CN",
                  "status":"ACTIVE"
                }
                """.formatted(tenantId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

      String createResponse =
        mockMvc
        .perform(
          post("/api/admin/knowledge-sources/web")
            .with(user(authUser("owner-web-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name":"产品帮助中心",
                "url":"https://example.com/help"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("WEB"))
        .andExpect(jsonPath("$.sourceName").value("产品帮助中心"))
        .andExpect(jsonPath("$.sourceUri").value("https://example.com/help"))
        .andReturn()
        .getResponse()
        .getContentAsString();

      long sourceId = JsonTestUtils.readLong(createResponse, "id");

      mockMvc
        .perform(
          get("/api/admin/knowledge-sources/{sourceId}", sourceId)
            .with(user(authUser("owner-web-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceUri").value("https://example.com/help"))
        .andExpect(jsonPath("$.metadata.url").value("https://example.com/help"));

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/web")
            .with(user(authUser("owner-web-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name":"坏链接",
                "url":"ftp://example.com/file.txt"
              }
              """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SOURCE_URL"));

      mockMvc
        .perform(
          get("/api/admin/knowledge-sources")
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-web-knowledge@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sourceType").value("WEB"))
        .andExpect(jsonPath("$[0].sourceUri").value("https://example.com/help"));
      }

      @Test
      void tenantAdminCanRefreshAndRetryKnowledgeSource() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
            "/docs",
            exchange -> {
              byte[] responseBody =
                  """
                  <html>
                    <head><title>刷新来源页面</title></head>
                    <body>
                      <main>
                        <p>最新帮助内容：订单支付后七天内可发起退款。</p>
                      </main>
                    </body>
                  </html>
                  """
                      .getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
              exchange.sendResponseHeaders(200, responseBody.length);
              exchange.getResponseBody().write(responseBody);
              exchange.close();
            });
        server.start();

        try {
          String tenantResponse =
              mockMvc
                  .perform(
                      post("/api/admin/tenants")
                          .with(user("admin@example.com").roles("SUPER_ADMIN"))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              """
                              {
                                "code":"tenant-refresh-knowledge",
                                "name":"Tenant Refresh Knowledge",
                                "contactName":"Rita",
                                "contactEmail":"rita@tenant.test",
                                "notes":"knowledge refresh",
                                "adminEmail":"owner-refresh-knowledge@tenant.test",
                                "adminDisplayName":"Owner Refresh Knowledge",
                                "adminPassword":"Tenant123!"
                              }
                              """))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

          String chatbotResponse =
              mockMvc
                  .perform(
                      post("/api/admin/chatbots")
                          .with(user(authUser("owner-refresh-knowledge@tenant.test")))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              """
                              {
                                "tenantId":%d,
                                "name":"Refresh Knowledge Bot",
                                "description":"refresh kb",
                                "language":"zh-CN",
                                "status":"ACTIVE"
                              }
                              """
                                  .formatted(tenantId)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

          String createResponse =
              mockMvc
                  .perform(
                      post("/api/admin/knowledge-sources/web")
                          .with(user(authUser("owner-refresh-knowledge@tenant.test")))
                          .param("tenantId", String.valueOf(tenantId))
                          .param("chatbotId", String.valueOf(chatbotId))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              """
                              {
                                "name":"刷新来源",
                                "url":"http://127.0.0.1:%d/docs"
                              }
                              """
                                  .formatted(server.getAddress().getPort())))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          long sourceId = JsonTestUtils.readLong(createResponse, "id");

          mockMvc
              .perform(
                  post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
                      .with(user(authUser("owner-refresh-knowledge@tenant.test")))
                      .param("tenantId", String.valueOf(tenantId))
                      .param("chatbotId", String.valueOf(chatbotId)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("ACTIVE"))
              .andExpect(jsonPath("$.metadata.lastRefreshResult").value("SUCCESS"))
              .andExpect(jsonPath("$.metadata.lastFetchedAt").exists())
              .andExpect(jsonPath("$.metadata.lastFetchedStatus").value(200))
              .andExpect(jsonPath("$.metadata.pageTitle").value("刷新来源页面"))
              .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0));

          var source = knowledgeSourceRepository.findById(sourceId).orElseThrow();
          source.setStatus(KnowledgeSourceStatus.FAILED);
          source.setFailureReason("FETCH_TIMEOUT");
          knowledgeSourceRepository.save(source);

          mockMvc
              .perform(
                  post("/api/admin/knowledge-sources/{sourceId}/retry", sourceId)
                      .with(user(authUser("owner-refresh-knowledge@tenant.test")))
                      .param("tenantId", String.valueOf(tenantId))
                      .param("chatbotId", String.valueOf(chatbotId)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("ACTIVE"))
              .andExpect(jsonPath("$.failureReason").isEmpty())
              .andExpect(jsonPath("$.metadata.retryCount").value(1))
              .andExpect(jsonPath("$.metadata.lastRetryAt").exists())
              .andExpect(jsonPath("$.metadata.pageTitle").value("刷新来源页面"));
        } finally {
          server.stop(0);
        }
      }

      @Test
      void tenantAdminCanDisableAndDeleteKnowledgeSource() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-delete-knowledge",
                  "name":"Tenant Delete Knowledge",
                  "contactName":"Diana",
                  "contactEmail":"diana@tenant.test",
                  "notes":"knowledge delete",
                  "adminEmail":"owner-delete-knowledge@tenant.test",
                  "adminDisplayName":"Owner Delete Knowledge",
                  "adminPassword":"Tenant123!"
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

      String chatbotResponse =
        mockMvc
          .perform(
            post("/api/admin/chatbots")
              .with(user(authUser("owner-delete-knowledge@tenant.test")))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Delete Knowledge Bot",
                  "description":"delete kb",
                  "language":"zh-CN",
                  "status":"ACTIVE"
                }
                """.formatted(tenantId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

      MockMultipartFile file =
        new MockMultipartFile(
          "file", "delete.txt", "text/plain", "delete me".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      String uploadResponse =
        mockMvc
          .perform(
            multipart("/api/admin/knowledge-sources/upload")
              .file(file)
              .param("tenantId", String.valueOf(tenantId))
              .param("chatbotId", String.valueOf(chatbotId))
              .with(user(authUser("owner-delete-knowledge@tenant.test"))))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
            .with(user(authUser("owner-delete-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0));

      mockMvc
        .perform(
          patch("/api/admin/knowledge-sources/{sourceId}/status", sourceId)
            .with(user(authUser("owner-delete-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "status":"DISABLED"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));

      mockMvc
        .perform(
          delete("/api/admin/knowledge-sources/{sourceId}", sourceId)
            .with(user(authUser("owner-delete-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELETED"))
        .andExpect(jsonPath("$.chunks").isEmpty());

      mockMvc
        .perform(
          get("/api/admin/knowledge-sources")
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .with(user(authUser("owner-delete-knowledge@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*]").isEmpty());
      }

  @Test
  void superAdminCanGetAndUpdateTenantDetail() throws Exception {
    String tenantResponse =
        mockMvc
            .perform(
                post("/api/admin/tenants")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"tenant-b",
                          "name":"Tenant B",
                          "contactName":"Bob",
                          "contactEmail":"bob@tenant.test",
                          "notes":"before update",
                          "adminEmail":"owner-b@tenant.test",
                          "adminDisplayName":"Owner B",
                          "adminPassword":"Tenant123!"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

    mockMvc
        .perform(get("/api/admin/tenants/{tenantId}", tenantId).with(user("admin@example.com").roles("SUPER_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("before update"))
        .andExpect(jsonPath("$.admin.email").value("owner-b@tenant.test"));

    mockMvc
        .perform(
            patch("/api/admin/tenants/{tenantId}", tenantId)
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"Tenant B Updated",
                      "contactName":"Bob Updated",
                      "contactEmail":"bob.updated@tenant.test",
                      "notes":"after update"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant B Updated"))
        .andExpect(jsonPath("$.contactEmail").value("bob.updated@tenant.test"))
        .andExpect(jsonPath("$.notes").value("after update"));
  }

  @Test
  void superAdminCanQueryAuditLogs() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/audit")
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .param("actionType", "AUTH_LOGIN"))
        .andExpect(status().isOk());
  }

  @Test
  void tenantAdminCanUpdateChatbotAndFaq() throws Exception {
    String tenantResponse =
        mockMvc
            .perform(
                post("/api/admin/tenants")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"tenant-c",
                          "name":"Tenant C",
                          "contactName":"Cathy",
                          "contactEmail":"cathy@tenant.test",
                          "notes":"tenant admin flows",
                          "adminEmail":"owner-c@tenant.test",
                          "adminDisplayName":"Owner C",
                          "adminPassword":"Tenant123!"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

    String chatbotResponse =
        mockMvc
            .perform(
                post("/api/admin/chatbots")
                  .with(user(authUser("owner-c@tenant.test")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "tenantId":%d,
                          "name":"Tenant Bot",
                          "description":"first",
                          "language":"zh-CN",
                          "status":"DRAFT"
                        }
                        """.formatted(tenantId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long chatbotId = JsonTestUtils.readLong(chatbotResponse, "id");

    mockMvc
        .perform(
            patch("/api/admin/chatbots/{chatbotId}", chatbotId)
              .with(user(authUser("owner-c@tenant.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"Tenant Bot Updated",
                      "description":"updated",
                      "language":"en-US",
                      "status":"ACTIVE"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant Bot Updated"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

      mockMvc
        .perform(
          patch("/api/admin/chatbots/{chatbotId}/appearance", chatbotId)
            .with(user(authUser("owner-c@tenant.test")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "themeColor":"#0f766e",
                "welcomeMessage":"欢迎来到支持台。",
                "brandVisible":false,
                "launcherPosition":"left"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.themeColor").value("#0f766e"))
        .andExpect(jsonPath("$.brandVisible").value(false));

      mockMvc
        .perform(
          patch("/api/admin/chatbots/{chatbotId}/behavior", chatbotId)
            .with(user(authUser("owner-c@tenant.test")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "fallbackMessage":"请留下联系方式，我们会尽快回复。",
                "allowDirectModel":true,
                "allowFeedback":false,
                "allowHandoff":true
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallbackMessage").value("请留下联系方式，我们会尽快回复。"))
        .andExpect(jsonPath("$.allowFeedback").value(false));

      mockMvc
        .perform(
          post("/api/admin/chatbots/{chatbotId}/copy", chatbotId)
            .with(user(authUser("owner-c@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Tenant Bot Updated Copy"))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    String faqResponse =
        mockMvc
            .perform(
                post("/api/admin/faqs")
                  .with(user(authUser("owner-c@tenant.test")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "tenantId":%d,
                          "chatbotId":%d,
                          "language":"zh-CN",
                          "question":"你们在哪里？",
                          "alternateQuestions":["办公地址在哪里"],
                          "answer":"我们在线提供支持。"
                        }
                        """.formatted(tenantId, chatbotId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long faqId = JsonTestUtils.readLong(faqResponse, "id");

    mockMvc
        .perform(
            patch("/api/admin/faqs/{faqId}", faqId)
              .with(user(authUser("owner-c@tenant.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "language":"zh-CN",
                      "question":"你们的办公地址？",
                      "alternateQuestions":["你们在哪里"],
                      "answer":"我们当前主要在线服务。"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.question").value("你们的办公地址？"));

    mockMvc
        .perform(
            patch("/api/admin/faqs/{faqId}/status", faqId)
              .with(user(authUser("owner-c@tenant.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "status":"DISABLED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));

            mockMvc
              .perform(
                get("/api/admin/faqs")
                  .with(user(authUser("owner-c@tenant.test")))
                  .param("tenantId", String.valueOf(tenantId))
                  .param("chatbotId", String.valueOf(chatbotId))
                  .param("keyword", "办公"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$[0].id").value(faqId));

            mockMvc
              .perform(
                patch("/api/admin/faqs/status")
                  .with(user(authUser("owner-c@tenant.test")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                    """
                    {
                      "faqIds":[%d],
                      "status":"ACTIVE"
                    }
                    """.formatted(faqId)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$[0].status").value("ACTIVE"));

            mockMvc
              .perform(
                get("/api/admin/faqs/export")
                  .with(user(authUser("owner-c@tenant.test")))
                  .param("tenantId", String.valueOf(tenantId))
                  .param("chatbotId", String.valueOf(chatbotId)))
              .andExpect(status().isOk())
              .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("faq-%d-%d.json".formatted(tenantId, chatbotId))))
              .andExpect(jsonPath("$.items[0].id").value(faqId));

          mockMvc
              .perform(
                  post("/api/admin/faqs/import")
                      .with(user(authUser("owner-c@tenant.test")))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "tenantId":%d,
                            "chatbotId":%d,
                            "items":[
                              {
                                "language":"zh-CN",
                                "status":"ACTIVE",
                                "question":"退款多久到账？",
                                "alternateQuestions":["退款时间多久"],
                                "answer":"通常 1 到 3 个工作日到账。"
                              },
                              {
                                "language":"zh-CN",
                                "status":"ACTIVE",
                                "question":"",
                                "alternateQuestions":[],
                                "answer":"无效记录"
                              }
                            ]
                          }
                          """.formatted(tenantId, chatbotId)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.importedCount").value(1))
              .andExpect(jsonPath("$.failures[0].index").value(1))
              .andExpect(jsonPath("$.failures[0].field").value("question"));

            mockMvc
              .perform(
                delete("/api/admin/chatbots/{chatbotId}", chatbotId)
                  .with(user(authUser("owner-c@tenant.test"))))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("DELETED"));
  }

      @Test
      void tenantAdminCanListViewAndUpdateConversation() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-conv",
                  "name":"Tenant Conversations",
                  "contactName":"Connie",
                  "contactEmail":"connie@tenant.test",
                  "notes":"conversation flows",
                  "adminEmail":"owner-conv@tenant.test",
                  "adminDisplayName":"Owner Conv",
                  "adminPassword":"Tenant123!"
                }
                """))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long tenantId = JsonTestUtils.readLong(tenantResponse, "id");

      String chatbotResponse =
        mockMvc
          .perform(
            post("/api/admin/chatbots")
              .with(user(authUser("owner-conv@tenant.test")))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Conversation Bot",
                  "description":"support",
                  "language":"zh-CN",
                  "status":"ACTIVE"
                }
                """.formatted(tenantId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      String initResponse =
        mockMvc
          .perform(
            post("/api/public/chat/init")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "chatbotPublicCode":"%s",
                  "entryType":"CHAT_PAGE",
                  "domain":"localhost",
                  "ipAddress":"127.0.0.1",
                  "userAgent":"JUnit"
                }
                """.formatted(publicCode)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long conversationId = JsonTestUtils.readLong(initResponse, "conversationId");

      mockMvc
        .perform(
          post("/api/public/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "conversationId":%d,
                "chatbotPublicCode":"%s",
                "language":"zh-CN",
                "message":"你好"
              }
              """.formatted(conversationId, publicCode)))
        .andExpect(status().isOk());

      mockMvc
        .perform(get("/api/admin/conversations").with(user(authUser("owner-conv@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(conversationId));

      mockMvc
        .perform(
          get("/api/admin/conversations/{conversationId}", conversationId)
            .with(user(authUser("owner-conv@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages").isArray())
        .andExpect(jsonPath("$.metadata.domain").value("localhost"));

      mockMvc
        .perform(
          patch("/api/admin/conversations/{conversationId}/status", conversationId)
            .with(user(authUser("owner-conv@tenant.test")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "status":"ENDED"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENDED"));

      mockMvc
        .perform(
          get("/api/admin/conversations/{conversationId}/export", conversationId)
            .with(user(authUser("owner-conv@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("conversation-%d.json".formatted(conversationId))))
        .andExpect(jsonPath("$.conversation.id").value(conversationId));

      mockMvc
        .perform(
          delete("/api/admin/conversations/{conversationId}", conversationId)
            .with(user(authUser("owner-conv@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELETED"));
      }
}
