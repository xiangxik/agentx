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

import com.agentx.backend.auth.application.DatabaseUserDetailsService;
import com.agentx.backend.knowledge.domain.KnowledgeChunkRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.agentx.backend.model.domain.ModelCallLog;
import com.agentx.backend.model.domain.ModelCallLogRepository;
import com.agentx.backend.model.domain.ModelCallStatus;
import com.agentx.backend.model.domain.ModelPurpose;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
  @Autowired private KnowledgeChunkRepository knowledgeChunkRepository;
  @Autowired private KnowledgeSourceRepository knowledgeSourceRepository;
  @Autowired private ModelCallLogRepository modelCallLogRepository;

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
    void superAdminCanManageModelProvidersAndDefinitions() throws Exception {
    String providerResponse =
      mockMvc
        .perform(
          post("/api/admin/model-providers")
            .with(user("admin@example.com").roles("SUPER_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "providerCode":"azure-openai",
                "displayName":"Azure OpenAI",
                "apiEndpoint":"https://azure.example.test/openai",
                "apiKey":"secret-987654",
                "status":"ACTIVE",
                "supports":"CHAT_COMPLETION,EMBEDDING",
                "transport":"OPENAI_COMPATIBLE",
                "apiKeyEnvVar":"AZURE_OPENAI_KEY",
                "apiVersion":"2024-02-15-preview"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiKeyHint").value("****7654"))
        .andExpect(jsonPath("$.transport").value("OPENAI_COMPATIBLE"))
        .andExpect(jsonPath("$.apiVersion").value("2024-02-15-preview"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    long providerId = JsonTestUtils.readLong(providerResponse, "id");

    String modelResponse =
      mockMvc
        .perform(
          post("/api/admin/model-providers/{providerId}/models", providerId)
            .with(user("admin@example.com").roles("SUPER_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "modelCode":"gpt-4.1-mini",
                "displayName":"GPT 4.1 Mini",
                "purpose":"CHAT_COMPLETION",
                "status":"ACTIVE",
                "isDefault":true,
                "inputPricePer1k":0.3,
                "outputPricePer1k":1.2,
                "maxTokens":4096
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providerCode").value("azure-openai"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    long modelId = JsonTestUtils.readLong(modelResponse, "id");

    mockMvc
      .perform(get("/api/admin/model-providers").with(user("admin@example.com").roles("SUPER_ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].providerCode").value("azure-openai"))
      .andExpect(jsonPath("$[0].apiKeyEnvVar").value("AZURE_OPENAI_KEY"))
      .andExpect(jsonPath("$[0].apiVersion").value("2024-02-15-preview"));

    mockMvc
      .perform(
        get("/api/admin/model-providers/models")
          .with(user("admin@example.com").roles("SUPER_ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].modelCode").value("gpt-4.1-mini"));

    mockMvc
      .perform(
        patch("/api/admin/model-providers/models/{modelId}/status", modelId)
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

    mockMvc
      .perform(
        patch("/api/admin/model-providers/{providerId}/status", providerId)
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

    mockMvc
      .perform(get("/api/admin/model-analytics").with(user("admin@example.com").roles("SUPER_ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCalls").exists())
      .andExpect(jsonPath("$.providers").isArray())
      .andExpect(jsonPath("$.models").isArray());
  }

  @Test
  void superAdminCanLoadAvailableModelsForProvider() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/models",
        exchange -> {
          byte[] responseBody =
              """
              {
                "data":[
                  {"id":"gpt-4.1-mini","name":"GPT 4.1 Mini"},
                  {"id":"gpt-4o-mini","name":"GPT 4o Mini"}
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.OPENAI_MODELS_KEY", "local-test-key");
    try {
      String providerResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"catalog-openai",
                            "displayName":"Catalog OpenAI",
                            "apiEndpoint":"http://127.0.0.1:%d/v1",
                            "apiKey":"secret-987654",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"OPENAI_COMPATIBLE",
                            "apiKeyEnvVar":"OPENAI_MODELS_KEY"
                          }
                          """
                              .formatted(server.getAddress().getPort())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      long providerId = JsonTestUtils.readLong(providerResponse, "id");

      mockMvc
          .perform(
              get("/api/admin/model-providers/{providerId}/available-models", providerId)
                  .with(user("admin@example.com").roles("SUPER_ADMIN")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].modelCode").value("gpt-4.1-mini"))
          .andExpect(jsonPath("$[0].displayName").value("GPT 4.1 Mini"))
          .andExpect(jsonPath("$[1].modelCode").value("gpt-4o-mini"));
    } finally {
      System.clearProperty("agentx.model.OPENAI_MODELS_KEY");
      server.stop(0);
    }
  }

  @Test
  void superAdminCanLoadQwenAvailableModelsFromDashScopeCatalog() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/api/v1/models",
        exchange -> {
          byte[] responseBody =
              """
              {
                "data":[
                  {"id":"qwen-plus","name":"Qwen Plus"},
                  {"id":"qwen-max","name":"Qwen Max"},
                  {"id":"text-embedding-v3","name":"Text Embedding V3"}
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.DASHSCOPE_MODELS_KEY", "local-test-key");
    try {
      String providerResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"catalog-qwen",
                            "displayName":"Catalog Qwen",
                            "apiEndpoint":"http://127.0.0.1:%d/compatible-mode/v1",
                            "apiKey":"secret-987654",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"QWEN_DASHSCOPE",
                            "apiKeyEnvVar":"DASHSCOPE_MODELS_KEY"
                          }
                          """
                              .formatted(server.getAddress().getPort())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      long providerId = JsonTestUtils.readLong(providerResponse, "id");

      mockMvc
          .perform(
              get("/api/admin/model-providers/{providerId}/available-models", providerId)
                  .with(user("admin@example.com").roles("SUPER_ADMIN")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].modelCode").value("qwen-plus"))
          .andExpect(jsonPath("$[1].modelCode").value("qwen-max"))
          .andExpect(jsonPath("$[2]").doesNotExist());
    } finally {
      System.clearProperty("agentx.model.DASHSCOPE_MODELS_KEY");
      server.stop(0);
    }
  }

  @Test
  void superAdminCannotCreateAnthropicEmbeddingModel() throws Exception {
    String providerResponse =
        mockMvc
            .perform(
                post("/api/admin/model-providers")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "providerCode":"anthropic-embed-test",
                          "displayName":"Anthropic Embed Test",
                          "apiEndpoint":"https://api.anthropic.test",
                          "apiKey":"secret-123456",
                          "status":"ACTIVE",
                          "supports":"CHAT_COMPLETION,EMBEDDING",
                          "transport":"ANTHROPIC",
                          "apiKeyEnvVar":"ANTHROPIC_TEST_KEY"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long providerId = JsonTestUtils.readLong(providerResponse, "id");

    mockMvc
        .perform(
            post("/api/admin/model-providers/{providerId}/models", providerId)
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "modelCode":"claude-embedding-1",
                      "displayName":"Claude Embedding 1",
                      "purpose":"EMBEDDING",
                      "status":"ACTIVE",
                      "isDefault":true,
                      "inputPricePer1k":0.02,
                      "outputPricePer1k":0.0,
                      "maxTokens":8192
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMBEDDING_TRANSPORT_UNSUPPORTED"));
  }

  @Test
  void superAdminCanFilterModelAnalyticsByCreatedAtWindow() throws Exception {
    ModelCallLog recentSuccess = new ModelCallLog();
    recentSuccess.setTenantId(1L);
    recentSuccess.setChatbotId(1L);
    recentSuccess.setConversationId(101L);
    recentSuccess.setProviderCode("provider-recent");
    recentSuccess.setModelCode("model-recent");
    recentSuccess.setPurpose(ModelPurpose.CHAT_COMPLETION);
    recentSuccess.setStatus(ModelCallStatus.SUCCESS);
    recentSuccess.setPromptTokens(120);
    recentSuccess.setCompletionTokens(30);
    recentSuccess.setTotalTokens(150);
    recentSuccess.setEstimatedCost(0.75);
    recentSuccess.setRetryCount(0);
    recentSuccess.setLatencyMs(180);
    recentSuccess.setMetadataJson("{}");
    recentSuccess.setCreatedAt(Instant.parse("2026-05-20T10:15:30Z"));
    modelCallLogRepository.save(recentSuccess);

    ModelCallLog oldFailure = new ModelCallLog();
    oldFailure.setTenantId(1L);
    oldFailure.setChatbotId(1L);
    oldFailure.setConversationId(102L);
    oldFailure.setProviderCode("provider-old");
    oldFailure.setModelCode("model-old");
    oldFailure.setPurpose(ModelPurpose.CHAT_COMPLETION);
    oldFailure.setStatus(ModelCallStatus.FAILED);
    oldFailure.setPromptTokens(80);
    oldFailure.setCompletionTokens(0);
    oldFailure.setTotalTokens(80);
    oldFailure.setEstimatedCost(0.0);
    oldFailure.setRetryCount(1);
    oldFailure.setLatencyMs(90);
    oldFailure.setErrorMessage("timeout");
    oldFailure.setMetadataJson("{}");
    oldFailure.setCreatedAt(Instant.parse("2026-01-10T08:00:00Z"));
    modelCallLogRepository.save(oldFailure);

    ModelCallLog recentFailure = new ModelCallLog();
    recentFailure.setTenantId(2L);
    recentFailure.setChatbotId(2L);
    recentFailure.setConversationId(103L);
    recentFailure.setProviderCode("provider-recent-b");
    recentFailure.setModelCode("model-recent-b");
    recentFailure.setPurpose(ModelPurpose.CHAT_COMPLETION);
    recentFailure.setStatus(ModelCallStatus.FAILED);
    recentFailure.setPromptTokens(40);
    recentFailure.setCompletionTokens(0);
    recentFailure.setTotalTokens(40);
    recentFailure.setEstimatedCost(0.1);
    recentFailure.setRetryCount(0);
    recentFailure.setLatencyMs(70);
    recentFailure.setErrorMessage("rate_limit");
    recentFailure.setMetadataJson("{}");
    recentFailure.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
    modelCallLogRepository.save(recentFailure);

    ModelCallLog previousSuccess = new ModelCallLog();
    previousSuccess.setTenantId(1L);
    previousSuccess.setChatbotId(1L);
    previousSuccess.setConversationId(104L);
    previousSuccess.setProviderCode("provider-prev");
    previousSuccess.setModelCode("model-prev");
    previousSuccess.setPurpose(ModelPurpose.CHAT_COMPLETION);
    previousSuccess.setStatus(ModelCallStatus.SUCCESS);
    previousSuccess.setPromptTokens(90);
    previousSuccess.setCompletionTokens(10);
    previousSuccess.setTotalTokens(100);
    previousSuccess.setEstimatedCost(0.25);
    previousSuccess.setRetryCount(0);
    previousSuccess.setLatencyMs(45);
    previousSuccess.setMetadataJson("{}");
    previousSuccess.setCreatedAt(Instant.parse("2026-04-15T09:30:00Z"));
    modelCallLogRepository.save(previousSuccess);

    mockMvc
      .perform(get("/api/admin/model-analytics").with(user("admin@example.com").roles("SUPER_ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCalls").value(4))
      .andExpect(jsonPath("$.failedCalls").value(2));

    mockMvc
      .perform(
        get("/api/admin/model-analytics")
          .with(user("admin@example.com").roles("SUPER_ADMIN"))
          .param("createdFrom", "2026-05-01T00:00:00Z")
          .param("createdTo", "2026-05-31T23:59:59Z"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCalls").value(2))
      .andExpect(jsonPath("$.successCalls").value(1))
      .andExpect(jsonPath("$.failedCalls").value(1))
      .andExpect(jsonPath("$.totalTokens").value(190))
      .andExpect(jsonPath("$.trends.totalCalls.previousValue").value(1.0))
      .andExpect(jsonPath("$.trends.totalCalls.deltaValue").value(1.0))
      .andExpect(jsonPath("$.trends.totalTokens.previousValue").value(100.0))
      .andExpect(jsonPath("$.providers[0].trends.totalCalls.previousValue").value(0.0))
      .andExpect(jsonPath("$.models[0].trends.totalCalls.previousValue").value(0.0))
      .andExpect(jsonPath("$.providers[0].providerCode").value("provider-recent"))
      .andExpect(jsonPath("$.models[0].modelCode").value("model-recent"));

    mockMvc
      .perform(
        get("/api/admin/model-analytics")
          .with(user("admin@example.com").roles("SUPER_ADMIN"))
          .param("createdFrom", "2026-05-01T00:00:00Z")
          .param("createdTo", "2026-05-31T23:59:59Z")
          .param("rowLimit", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.providers.length()").value(1))
      .andExpect(jsonPath("$.models.length()").value(1));

    mockMvc
      .perform(
        get("/api/admin/model-analytics/export")
          .with(user("admin@example.com").roles("SUPER_ADMIN"))
          .param("tenantId", "1")
          .param("providerCode", "provider-recent")
          .param("modelCode", "model-recent")
          .param("createdFrom", "2026-05-01T00:00:00Z")
          .param("createdTo", "2026-05-31T23:59:59Z")
          .param("rowLimit", "1"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("model-analytics-tenant-1-provider-provider-recent-model-model-recent-20260501T000000Z_to_20260531T235959Z-top-1.csv")))
      .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("section,metric,current_value,previous_value,delta_value,delta_percent")))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("provider,\"provider-recent\"")))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("previous_total_calls,delta_total_calls,delta_total_calls_percent")));

    mockMvc
      .perform(
        get("/api/admin/model-analytics")
          .with(user("admin@example.com").roles("SUPER_ADMIN"))
          .param("tenantId", "1")
          .param("providerCode", "provider-old")
          .param("modelCode", "model-old"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCalls").value(1))
      .andExpect(jsonPath("$.successCalls").value(0))
      .andExpect(jsonPath("$.failedCalls").value(1))
      .andExpect(jsonPath("$.providers[0].providerCode").value("provider-old"))
      .andExpect(jsonPath("$.models[0].modelCode").value("model-old"));

    mockMvc
      .perform(
        get("/api/admin/model-analytics")
          .with(user("admin@example.com").roles("SUPER_ADMIN"))
          .param("tenantId", "999"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalCalls").value(0))
      .andExpect(jsonPath("$.providers").isArray())
      .andExpect(jsonPath("$.models").isArray());
  }

  @Test
  void nonSuperAdminCannotListTenants() throws Exception {
    mockMvc
        .perform(get("/api/admin/tenants").with(user("editor@example.com").roles("TENANT_ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantAdminCanReadModelCatalogEndpoints() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/api/v1/models",
        exchange -> {
          byte[] responseBody =
              """
              {
                "data":[
                  {"id":"qwen-plus","name":"Qwen Plus"}
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.TENANT_DASHSCOPE_MODELS_KEY", "local-test-key");
    try {
      String providerResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"tenant-qwen-catalog",
                            "displayName":"Tenant Qwen Catalog",
                            "apiEndpoint":"http://127.0.0.1:%d/compatible-mode/v1",
                            "apiKey":"secret-987654",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"QWEN_DASHSCOPE",
                            "apiKeyEnvVar":"TENANT_DASHSCOPE_MODELS_KEY"
                          }
                          """
                              .formatted(server.getAddress().getPort())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      long providerId = JsonTestUtils.readLong(providerResponse, "id");

      mockMvc
          .perform(
              post("/api/admin/model-providers/{providerId}/models", providerId)
                  .with(user("admin@example.com").roles("SUPER_ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "modelCode":"qwen-plus",
                        "displayName":"Qwen Plus",
                        "purpose":"CHAT_COMPLETION",
                        "status":"ACTIVE",
                        "isDefault":true,
                        "inputPricePer1k":0.15,
                        "outputPricePer1k":0.6,
                        "maxTokens":2048
                      }
                      """))
          .andExpect(status().isOk());

      mockMvc
          .perform(
              post("/api/admin/tenants")
                  .with(user("admin@example.com").roles("SUPER_ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "code":"tenant-model-reader",
                        "name":"Tenant Model Reader",
                        "contactName":"Alice",
                        "contactEmail":"alice-reader@tenant.test",
                        "notes":"model reader tenant",
                        "adminEmail":"owner-model-reader@tenant.test",
                        "adminDisplayName":"Owner Model Reader",
                        "adminPassword":"Tenant123!"
                      }
                      """))
          .andExpect(status().isOk());

      UserDetails tenantAdmin = authUser("owner-model-reader@tenant.test");

      mockMvc
          .perform(get("/api/admin/model-providers").with(user(tenantAdmin)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].providerCode").value("tenant-qwen-catalog"));

      mockMvc
          .perform(get("/api/admin/model-providers/models").with(user(tenantAdmin)).param("purpose", "CHAT_COMPLETION"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].providerCode").value("tenant-qwen-catalog"))
          .andExpect(jsonPath("$[0].modelCode").value("qwen-plus"));

      mockMvc
          .perform(
              get("/api/admin/model-providers/{providerId}/available-models", providerId)
                  .with(user(tenantAdmin)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].modelCode").value("qwen-plus"));
    } finally {
      System.clearProperty("agentx.model.TENANT_DASHSCOPE_MODELS_KEY");
      server.stop(0);
    }
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
                .andExpect(jsonPath("$.metadata.embeddedChunkCount").value(1))
              .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0));

              var refreshedChunk =
                knowledgeChunkRepository.findByKnowledgeSourceIdOrderByChunkIndexAsc(sourceId).get(0);
              org.junit.jupiter.api.Assertions.assertNotNull(refreshedChunk.getEmbeddingRef());
              org.junit.jupiter.api.Assertions.assertFalse(refreshedChunk.getEmbeddingRef().isBlank());
              org.junit.jupiter.api.Assertions.assertNotNull(refreshedChunk.getEmbeddingDimension());
              org.junit.jupiter.api.Assertions.assertTrue(refreshedChunk.getEmbeddingDimension() > 0);

              long embeddingLogCount =
                modelCallLogRepository.findAll().stream()
                  .filter(log -> log.getTenantId().equals(tenantId))
                  .filter(log -> log.getChatbotId() != null && log.getChatbotId().equals(chatbotId))
                  .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
                  .count();
              org.junit.jupiter.api.Assertions.assertEquals(1L, embeddingLogCount);

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
                "launcherPosition":"left",
                "stylePreset":"forest"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.themeColor").value("#0f766e"))
        .andExpect(jsonPath("$.brandVisible").value(false))
        .andExpect(jsonPath("$.stylePreset").value("forest"));

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
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("conversation-%d-conversation-bot.json".formatted(conversationId))))
        .andExpect(jsonPath("$.conversation.id").value(conversationId))
        .andExpect(jsonPath("$.summary.chatbotName").value("Conversation Bot"))
        .andExpect(jsonPath("$.summary.messageCount").value(2))
        .andExpect(jsonPath("$.summary.modelCallCount").value(0));

      mockMvc
        .perform(
          delete("/api/admin/conversations/{conversationId}", conversationId)
            .with(user(authUser("owner-conv@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELETED"));
      }
}
