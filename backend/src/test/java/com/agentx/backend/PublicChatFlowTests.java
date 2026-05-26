package com.agentx.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.application.DatabaseUserDetailsService;
import com.agentx.backend.auth.application.BootstrapDataInitializer;
import com.agentx.backend.model.domain.ModelCallLogRepository;
import com.agentx.backend.model.domain.ModelPurpose;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PublicChatFlowTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private BootstrapDataInitializer bootstrapDataInitializer;
  @Autowired private DatabaseUserDetailsService userDetailsService;
  @Autowired private ModelCallLogRepository modelCallLogRepository;

  private UserDetails authUser(String email) {
    return userDetailsService.loadUserByUsername(email);
  }

  @BeforeEach
  void setUp() {
    bootstrapDataInitializer.ensureRole("TENANT_ADMIN", "租户管理员");
  }

  @Test
  void publicChatInitReturnsBadRequestWhenChatbotDoesNotExist() throws Exception {
    mockMvc
        .perform(
            post("/api/public/chat/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "chatbotPublicCode":"missing-bot",
                      "entryType":"CHAT_PAGE",
                      "domain":"localhost",
                      "ipAddress":"127.0.0.1",
                      "userAgent":"JUnit"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CHATBOT_NOT_FOUND"));
  }

  @Test
  void publicChatReturnsFaqAnswerWhenMatched() throws Exception {
    String tenantResponse =
        mockMvc
            .perform(
                post("/api/admin/tenants")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "code":"tenant-chat",
                                  "name":"Tenant Chat",
                                  "contactName":"Alice",
                                  "contactEmail":"alice@tenant.test",
                                  "notes":"chat tenant",
                                  "adminEmail":"owner-chat@tenant.test",
                                  "adminDisplayName":"Owner Chat",
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
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "tenantId":%d,
                                  "name":"Support Bot",
                                  "description":"support",
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
    String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

    mockMvc
        .perform(
            post("/api/admin/faqs")
                .with(user("admin@example.com").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "tenantId":%d,
                                  "chatbotId":%d,
                                  "language":"zh-CN",
                                  "question":"你们的客服时间？",
                                  "alternateQuestions":["营业时间是什么时候"],
                                  "answer":"我们的客服时间为工作日 9:00-18:00。"
                                }
                                """
                        .formatted(tenantId, chatbotId)))
        .andExpect(status().isOk());

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
                                """
                            .formatted(publicCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.welcomeMessage").isNotEmpty())
            .andExpect(jsonPath("$.brandVisible").value(true))
            .andExpect(jsonPath("$.stylePreset").value("executive"))
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
                                  "message":"你们的客服时间？"
                                }
                                """
                        .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("FAQ"))
        .andExpect(jsonPath("$.answer").value("我们的客服时间为工作日 9:00-18:00。"));
  }

      @Test
      void publicChatUsesModelReplyWhenDirectModelIsEnabled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
            "/v1/chat/completions",
            exchange -> {
              byte[] responseBody =
                  """
                  {
                    "choices":[
                      {
                        "message":{
                          "role":"assistant",
                          "content":"这是 OpenAI 兼容供应商返回的退款进度建议。"
                        }
                      }
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

        System.setProperty("agentx.model.OPENAI_TEST_KEY", "local-test-key");
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
                    "providerCode":"mock-openai",
                    "displayName":"Mock OpenAI",
                          "apiEndpoint":"http://127.0.0.1:%d/v1",
                          "apiKey":"sk-test-123456",
                    "status":"ACTIVE",
                          "supports":"CHAT_COMPLETION",
                          "transport":"OPENAI_COMPATIBLE",
                          "apiKeyEnvVar":"OPENAI_TEST_KEY"
                  }
                        """.formatted(server.getAddress().getPort())))
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
                  "modelCode":"gpt-4o-mini",
                  "displayName":"GPT-4o Mini",
                  "purpose":"CHAT_COMPLETION",
                  "status":"ACTIVE",
                  "isDefault":true,
                  "inputPricePer1k":0.15,
                  "outputPricePer1k":0.6,
                  "maxTokens":2048
                }
                """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.isDefault").value(true));

      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"tenant-model-chat",
                  "name":"Tenant Model Chat",
                  "contactName":"Alice",
                  "contactEmail":"alice-model@tenant.test",
                  "notes":"model chat tenant",
                  "adminEmail":"owner-model-chat@tenant.test",
                  "adminDisplayName":"Owner Model Chat",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Model Bot",
                  "description":"model",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      mockMvc
        .perform(
          patch("/api/admin/chatbots/{chatbotId}/behavior", chatbotId)
            .with(user(authUser("owner-model-chat@tenant.test")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "fallbackMessage":"暂时没有标准答案。",
                "allowDirectModel":true,
                "allowFeedback":true,
                "allowHandoff":true
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowDirectModel").value(true));

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
                """
                  .formatted(publicCode)))
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
                "message":"我要咨询退款进度"
              }
              """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("MODEL"))
        .andExpect(jsonPath("$.citations").isEmpty())
        .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("退款进度")));

      mockMvc
        .perform(
          get("/api/admin/conversations/{conversationId}", conversationId)
            .with(user(authUser("owner-model-chat@tenant.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[1].sourceType").value("MODEL"))
        .andExpect(jsonPath("$.messages[1].model.provider").value("mock-openai"))
          .andExpect(jsonPath("$.modelCalls[0].model").value("gpt-4o-mini"))
          .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("OpenAI 兼容供应商返回")));
      } finally {
        System.clearProperty("agentx.model.OPENAI_TEST_KEY");
        server.stop(0);
      }
      }

  @Test
  void publicChatUsesQwenDashScopeReplyWhenDirectModelIsEnabled() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] responseBody =
              """
              {
                "output":{
                  "choices":[
                    {
                      "message":{
                        "role":"assistant",
                        "content":"这是阿里云百炼 Qwen 返回的退款进度建议。"
                      }
                    }
                  ]
                }
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.DASHSCOPE_TEST_KEY", "local-test-key");
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
                            "providerCode":"mock-qwen",
                            "displayName":"Mock Qwen",
                            "apiEndpoint":"http://127.0.0.1:%d",
                            "apiKey":"sk-test-123456",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"QWEN_DASHSCOPE",
                            "apiKeyEnvVar":"DASHSCOPE_TEST_KEY"
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
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.isDefault").value(true));

      String tenantResponse =
          mockMvc
              .perform(
                  post("/api/admin/tenants")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "code":"tenant-qwen-chat",
                            "name":"Tenant Qwen Chat",
                            "contactName":"Alice",
                            "contactEmail":"alice-qwen@tenant.test",
                            "notes":"qwen chat tenant",
                            "adminEmail":"owner-qwen-chat@tenant.test",
                            "adminDisplayName":"Owner Qwen Chat",
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
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Qwen Bot",
                            "description":"qwen",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      mockMvc
          .perform(
              patch("/api/admin/chatbots/{chatbotId}/behavior", chatbotId)
                  .with(user(authUser("owner-qwen-chat@tenant.test")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "fallbackMessage":"暂时没有标准答案。",
                        "allowDirectModel":true,
                        "allowFeedback":true,
                        "allowHandoff":true
                      }
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.allowDirectModel").value(true));

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
                          """
                              .formatted(publicCode)))
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
                        "message":"我要咨询退款进度"
                      }
                      """
                          .formatted(conversationId, publicCode)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sourceType").value("MODEL"))
          .andExpect(jsonPath("$.citations").isEmpty())
          .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("退款进度")));

      mockMvc
          .perform(
              get("/api/admin/conversations/{conversationId}", conversationId)
                  .with(user(authUser("owner-qwen-chat@tenant.test"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages[1].sourceType").value("MODEL"))
          .andExpect(jsonPath("$.messages[1].model.provider").value("mock-qwen"))
          .andExpect(jsonPath("$.modelCalls[0].model").value("qwen-plus"))
          .andExpect(
              jsonPath("$.messages[1].content")
                  .value(org.hamcrest.Matchers.containsString("阿里云百炼 Qwen 返回")));
    } finally {
      System.clearProperty("agentx.model.DASHSCOPE_TEST_KEY");
      server.stop(0);
    }
  }

  @Test
  void publicChatUsesChatbotSelectedModelInsteadOfSystemDefault() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/default/v1/chat/completions",
        exchange -> {
          byte[] responseBody =
              """
              {
                "choices":[
                  {
                    "message":{
                      "role":"assistant",
                      "content":"这是系统默认模型返回的答复。"
                    }
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.createContext(
        "/selected/v1/chat/completions",
        exchange -> {
          byte[] responseBody =
              """
              {
                "choices":[
                  {
                    "message":{
                      "role":"assistant",
                      "content":"这是 Chatbot 指定模型返回的答复。"
                    }
                  }
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

    System.setProperty("agentx.model.DEFAULT_OPENAI_KEY", "local-test-key");
    System.setProperty("agentx.model.SELECTED_OPENAI_KEY", "local-test-key");
    try {
      String defaultProviderResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"default-openai",
                            "displayName":"Default OpenAI",
                            "apiEndpoint":"http://127.0.0.1:%d/default/v1",
                            "apiKey":"sk-test-123456",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"OPENAI_COMPATIBLE",
                            "apiKeyEnvVar":"DEFAULT_OPENAI_KEY"
                          }
                          """
                              .formatted(server.getAddress().getPort())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      long defaultProviderId = JsonTestUtils.readLong(defaultProviderResponse, "id");

      mockMvc
          .perform(
              post("/api/admin/model-providers/{providerId}/models", defaultProviderId)
                  .with(user("admin@example.com").roles("SUPER_ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "modelCode":"gpt-default",
                        "displayName":"GPT Default",
                        "purpose":"CHAT_COMPLETION",
                        "status":"ACTIVE",
                        "isDefault":true,
                        "inputPricePer1k":0.15,
                        "outputPricePer1k":0.6,
                        "maxTokens":2048
                      }
                      """))
          .andExpect(status().isOk());

      String selectedProviderResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"selected-openai",
                            "displayName":"Selected OpenAI",
                            "apiEndpoint":"http://127.0.0.1:%d/selected/v1",
                            "apiKey":"sk-test-123456",
                            "status":"ACTIVE",
                            "supports":"CHAT_COMPLETION",
                            "transport":"OPENAI_COMPATIBLE",
                            "apiKeyEnvVar":"SELECTED_OPENAI_KEY"
                          }
                          """
                              .formatted(server.getAddress().getPort())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      long selectedProviderId = JsonTestUtils.readLong(selectedProviderResponse, "id");

      mockMvc
          .perform(
              post("/api/admin/model-providers/{providerId}/models", selectedProviderId)
                  .with(user("admin@example.com").roles("SUPER_ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "modelCode":"gpt-selected",
                        "displayName":"GPT Selected",
                        "purpose":"CHAT_COMPLETION",
                        "status":"ACTIVE",
                        "isDefault":false,
                        "inputPricePer1k":0.15,
                        "outputPricePer1k":0.6,
                        "maxTokens":2048
                      }
                      """))
          .andExpect(status().isOk());

      String tenantResponse =
          mockMvc
              .perform(
                  post("/api/admin/tenants")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "code":"tenant-selected-model",
                            "name":"Tenant Selected Model",
                            "contactName":"Alice",
                            "contactEmail":"alice-selected@tenant.test",
                            "notes":"selected model tenant",
                            "adminEmail":"owner-selected@tenant.test",
                            "adminDisplayName":"Owner Selected",
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
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Selected Model Bot",
                            "description":"model select",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      mockMvc
          .perform(
              patch("/api/admin/chatbots/{chatbotId}/behavior", chatbotId)
                  .with(user(authUser("owner-selected@tenant.test")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "fallbackMessage":"暂时没有标准答案。",
                        "allowDirectModel":true,
                        "allowFeedback":true,
                        "allowHandoff":true,
                        "providerCode":"selected-openai",
                        "modelCode":"gpt-selected"
                      }
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.providerCode").value("selected-openai"))
          .andExpect(jsonPath("$.modelCode").value("gpt-selected"));

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
                          """
                              .formatted(publicCode)))
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
                        "message":"我要咨询退款进度"
                      }
                      """
                          .formatted(conversationId, publicCode)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sourceType").value("MODEL"))
          .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Chatbot 指定模型返回")));

      mockMvc
          .perform(
              get("/api/admin/conversations/{conversationId}", conversationId)
                  .with(user(authUser("owner-selected@tenant.test"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.messages[1].model.provider").value("selected-openai"))
          .andExpect(jsonPath("$.messages[1].model.model").value("gpt-selected"))
          .andExpect(jsonPath("$.modelCalls[0].provider").value("selected-openai"))
          .andExpect(jsonPath("$.modelCalls[0].model").value("gpt-selected"));
    } finally {
      System.clearProperty("agentx.model.DEFAULT_OPENAI_KEY");
      System.clearProperty("agentx.model.SELECTED_OPENAI_KEY");
      server.stop(0);
    }
  }

      @Test
      void publicChatReturnsKnowledgeAnswerWhenFaqMisses() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "code":"tenant-knowledge-chat",
                      "name":"Tenant Knowledge Chat",
                      "contactName":"Alice",
                      "contactEmail":"alice-knowledge@tenant.test",
                      "notes":"knowledge chat tenant",
                      "adminEmail":"owner-knowledge-chat@tenant.test",
                      "adminDisplayName":"Owner Knowledge Chat",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "tenantId":%d,
                      "name":"Knowledge Bot",
                      "description":"knowledge",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      MockMultipartFile file =
        new MockMultipartFile(
          "file",
          "policy.txt",
          "text/plain",
          "退款规则：订单支付后七天内可以原路退款。".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      String uploadResponse =
        mockMvc
          .perform(
            multipart("/api/admin/knowledge-sources/upload")
              .file(file)
              .with(user(authUser("owner-knowledge-chat@tenant.test")))
              .param("tenantId", String.valueOf(tenantId))
              .param("chatbotId", String.valueOf(chatbotId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
            .with(user(authUser("owner-knowledge-chat@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.chunkCount").value(1));

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
                    """
                  .formatted(publicCode)))
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
                      "message":"退款规则是什么？"
                    }
                    """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"))
        .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("退款规则")))
        .andExpect(jsonPath("$.citations[0].sourceType").value("KNOWLEDGE"))
        .andExpect(jsonPath("$.citations[0].sourceLink").value(org.hamcrest.Matchers.nullValue()));
      }

      @Test
      void publicChatSkipsDisabledKnowledgeSourceUntilReenabled() throws Exception {
      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "code":"tenant-knowledge-toggle",
                      "name":"Tenant Knowledge Toggle",
                      "contactName":"Alice",
                      "contactEmail":"alice-toggle@tenant.test",
                      "notes":"knowledge toggle tenant",
                      "adminEmail":"owner-knowledge-toggle@tenant.test",
                      "adminDisplayName":"Owner Knowledge Toggle",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "tenantId":%d,
                      "name":"Knowledge Toggle Bot",
                      "description":"knowledge toggle",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      MockMultipartFile file =
        new MockMultipartFile(
          "file",
          "toggle.txt",
          "text/plain",
          "退款规则：订单支付后七天内可以原路退款。".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      String uploadResponse =
        mockMvc
          .perform(
            multipart("/api/admin/knowledge-sources/upload")
              .file(file)
              .with(user(authUser("owner-knowledge-toggle@tenant.test")))
              .param("tenantId", String.valueOf(tenantId))
              .param("chatbotId", String.valueOf(chatbotId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
            .with(user(authUser("owner-knowledge-toggle@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk());

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
                    """
                  .formatted(publicCode)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long conversationId = JsonTestUtils.readLong(initResponse, "conversationId");

      mockMvc
        .perform(
          patch("/api/admin/knowledge-sources/{sourceId}/status", sourceId)
            .with(user(authUser("owner-knowledge-toggle@tenant.test")))
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
          post("/api/public/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
                    {
                      "conversationId":%d,
                      "chatbotPublicCode":"%s",
                      "language":"zh-CN",
                      "message":"退款规则是什么？"
                    }
                    """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("FALLBACK"));

      mockMvc
        .perform(
          patch("/api/admin/knowledge-sources/{sourceId}/status", sourceId)
            .with(user(authUser("owner-knowledge-toggle@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "status":"ACTIVE"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

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
                      "message":"退款规则是什么？"
                    }
                    """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"))
          .andExpect(jsonPath("$.citations[0].sourceType").value("KNOWLEDGE"))
          .andExpect(jsonPath("$.citations[0].sourceLink").value(org.hamcrest.Matchers.nullValue()));
      }

      @Test
      void publicChatReturnsWebKnowledgeCitationLink() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
            "/help",
            exchange -> {
              byte[] responseBody =
                  """
                  <html>
                    <head><title>帮助中心</title></head>
                    <body>
                      <article>
                        <h1>退款说明</h1>
                        <p>订单支付后七天内可以在线提交退款申请。</p>
                        <p>如需人工处理，请联系帮助中心。</p>
                      </article>
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
                      "code":"tenant-web-knowledge-chat",
                      "name":"Tenant Web Knowledge Chat",
                      "contactName":"Alice",
                      "contactEmail":"alice-web-chat@tenant.test",
                      "notes":"web knowledge chat tenant",
                      "adminEmail":"owner-web-knowledge-chat@tenant.test",
                      "adminDisplayName":"Owner Web Knowledge Chat",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "tenantId":%d,
                      "name":"Web Knowledge Bot",
                      "description":"web knowledge",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      String createResponse =
        mockMvc
          .perform(
            post("/api/admin/knowledge-sources/web")
              .with(user(authUser("owner-web-knowledge-chat@tenant.test")))
              .param("tenantId", String.valueOf(tenantId))
              .param("chatbotId", String.valueOf(chatbotId))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                    {
                      "name":"帮助中心",
                      "url":"http://127.0.0.1:%d/help"
                    }
                    """.formatted(server.getAddress().getPort())))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();

      long sourceId = JsonTestUtils.readLong(createResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
            .with(user(authUser("owner-web-knowledge-chat@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

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
                    """
                  .formatted(publicCode)))
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
                      "message":"退款规则是什么？"
                    }
                    """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"))
        .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("七天内可以在线提交退款申请")))
        .andExpect(jsonPath("$.citations[0].sourceType").value("KNOWLEDGE"))
        .andExpect(jsonPath("$.citations[0].title").value("帮助中心"))
        .andExpect(
            jsonPath("$.citations[0].sourceLink")
                .value("http://127.0.0.1:%d/help".formatted(server.getAddress().getPort())));
    } finally {
      server.stop(0);
    }
      }

  @Test
  void publicChatUsesExternalEmbeddingModelForKnowledgeRetrieval() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    AtomicInteger embeddingRequestCount = new AtomicInteger();
    server.createContext(
        "/v1/embeddings",
        exchange -> {
          embeddingRequestCount.incrementAndGet();
          String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          String vector =
              requestBody.contains("履约周期") || requestBody.contains("多久能收到包裹")
                  ? "[0.99,0.01,0.01]"
                  : "[0.01,0.99,0.01]";
          byte[] responseBody =
              ("""
              {
                "data":[
                  {"embedding":%s}
                ]
              }
              """
                  .formatted(vector))
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.OPENAI_EMBED_KEY", "local-embed-key");
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
                            "code":"tenant-embedding-search",
                            "name":"Tenant Embedding Search",
                            "contactName":"Alice",
                            "contactEmail":"alice-embedding@tenant.test",
                            "notes":"embedding retrieval tenant",
                            "adminEmail":"owner-embedding@tenant.test",
                            "adminDisplayName":"Owner Embedding",
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
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Embedding Search Bot",
                            "description":"embedding retrieval",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      String providerResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"openai-embed",
                            "displayName":"OpenAI Embedding",
                            "apiEndpoint":"http://127.0.0.1:%d/v1",
                            "apiKey":"secret-embedding",
                            "status":"ACTIVE",
                            "supports":"EMBEDDING",
                            "transport":"OPENAI_COMPATIBLE",
                            "apiKeyEnvVar":"OPENAI_EMBED_KEY"
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
                        "modelCode":"text-embedding-3-small",
                        "displayName":"Text Embedding 3 Small",
                        "purpose":"EMBEDDING",
                        "status":"ACTIVE",
                        "isDefault":true,
                        "inputPricePer1k":0.02,
                        "outputPricePer1k":0.0,
                        "maxTokens":8192
                      }
                      """))
          .andExpect(status().isOk());

      MockMultipartFile file =
          new MockMultipartFile(
              "file",
              "delivery.txt",
              "text/plain",
              "履约周期：常规派送 72 小时完成。".getBytes(StandardCharsets.UTF_8));

      String uploadResponse =
          mockMvc
              .perform(
                  multipart("/api/admin/knowledge-sources/upload")
                      .file(file)
                      .with(user(authUser("owner-embedding@tenant.test")))
                      .param("tenantId", String.valueOf(tenantId))
                      .param("chatbotId", String.valueOf(chatbotId)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
          .perform(
              post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
                  .with(user(authUser("owner-embedding@tenant.test")))
                  .param("tenantId", String.valueOf(tenantId))
                  .param("chatbotId", String.valueOf(chatbotId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.metadata.embeddingProviderCode").value("openai-embed"))
          .andExpect(jsonPath("$.metadata.embeddingModelCode").value("text-embedding-3-small"));

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
                          """
                              .formatted(publicCode)))
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
                        "message":"多久能收到包裹？"
                      }
                      """
                          .formatted(conversationId, publicCode)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"))
          .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("72 小时完成")));

      long embeddingLogCount =
          modelCallLogRepository.findAll().stream()
              .filter(log -> log.getTenantId().equals(tenantId))
              .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
              .filter(log -> "openai-embed".equals(log.getProviderCode()))
              .filter(log -> "text-embedding-3-small".equals(log.getModelCode()))
              .count();
        org.junit.jupiter.api.Assertions.assertEquals(2L, embeddingLogCount);
        org.junit.jupiter.api.Assertions.assertTrue(
          modelCallLogRepository.findAll().stream()
            .filter(log -> log.getTenantId().equals(tenantId))
            .filter(log -> log.getConversationId() != null && log.getConversationId().equals(conversationId))
            .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
            .anyMatch(log -> log.getMetadataJson().contains("KNOWLEDGE_QUERY")));
      org.junit.jupiter.api.Assertions.assertTrue(embeddingRequestCount.get() >= 2);
    } finally {
      System.clearProperty("agentx.model.OPENAI_EMBED_KEY");
      server.stop(0);
    }
  }

  @Test
  void publicChatUsesQwenEmbeddingModelForKnowledgeRetrieval() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    AtomicInteger embeddingRequestCount = new AtomicInteger();
    server.createContext(
        "/compatible-mode/v1/embeddings",
        exchange -> {
          embeddingRequestCount.incrementAndGet();
          String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          String vector =
              requestBody.contains("开票周期") || requestBody.contains("多久能拿到发票")
                  ? "[0.99,0.02,0.01]"
                  : "[0.01,0.98,0.02]";
          byte[] responseBody =
              ("""
              {
                "data":[
                  {"embedding":%s}
                ]
              }
              """
                  .formatted(vector))
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();

    System.setProperty("agentx.model.QWEN_EMBED_KEY", "local-qwen-embed-key");
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
                            "code":"tenant-qwen-embedding-search",
                            "name":"Tenant Qwen Embedding Search",
                            "contactName":"Alice",
                            "contactEmail":"alice-qwen-embedding@tenant.test",
                            "notes":"qwen embedding retrieval tenant",
                            "adminEmail":"owner-qwen-embedding@tenant.test",
                            "adminDisplayName":"Owner Qwen Embedding",
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
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "tenantId":%d,
                            "name":"Qwen Embedding Search Bot",
                            "description":"qwen embedding retrieval",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      String providerResponse =
          mockMvc
              .perform(
                  post("/api/admin/model-providers")
                      .with(user("admin@example.com").roles("SUPER_ADMIN"))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "providerCode":"qwen-embed",
                            "displayName":"Qwen Embedding",
                            "apiEndpoint":"http://127.0.0.1:%d/compatible-mode/v1",
                            "apiKey":"secret-qwen-embedding",
                            "status":"ACTIVE",
                            "supports":"EMBEDDING",
                            "transport":"QWEN_DASHSCOPE",
                            "apiKeyEnvVar":"QWEN_EMBED_KEY"
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
                        "modelCode":"text-embedding-v4",
                        "displayName":"Qwen Text Embedding v4",
                        "purpose":"EMBEDDING",
                        "status":"ACTIVE",
                        "isDefault":true,
                        "inputPricePer1k":0.01,
                        "outputPricePer1k":0.0,
                        "maxTokens":8192
                      }
                      """))
          .andExpect(status().isOk());

      MockMultipartFile file =
          new MockMultipartFile(
              "file",
              "invoice.txt",
              "text/plain",
              "开票周期：电子发票会在 2 小时内发送到预留邮箱。".getBytes(StandardCharsets.UTF_8));

      String uploadResponse =
          mockMvc
              .perform(
                  multipart("/api/admin/knowledge-sources/upload")
                      .file(file)
                      .with(user(authUser("owner-qwen-embedding@tenant.test")))
                      .param("tenantId", String.valueOf(tenantId))
                      .param("chatbotId", String.valueOf(chatbotId)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
          .perform(
              post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
                  .with(user(authUser("owner-qwen-embedding@tenant.test")))
                  .param("tenantId", String.valueOf(tenantId))
                  .param("chatbotId", String.valueOf(chatbotId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.metadata.embeddingProviderCode").value("qwen-embed"))
          .andExpect(jsonPath("$.metadata.embeddingModelCode").value("text-embedding-v4"));

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
                          """
                              .formatted(publicCode)))
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
                        "message":"多久能拿到发票？"
                      }
                      """
                          .formatted(conversationId, publicCode)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"))
          .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("2 小时内发送")));

      long embeddingLogCount =
          modelCallLogRepository.findAll().stream()
              .filter(log -> log.getTenantId().equals(tenantId))
              .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
              .filter(log -> "qwen-embed".equals(log.getProviderCode()))
              .filter(log -> "text-embedding-v4".equals(log.getModelCode()))
              .count();
        org.junit.jupiter.api.Assertions.assertEquals(2L, embeddingLogCount);
        org.junit.jupiter.api.Assertions.assertTrue(
          modelCallLogRepository.findAll().stream()
            .filter(log -> log.getTenantId().equals(tenantId))
            .filter(log -> log.getConversationId() != null && log.getConversationId().equals(conversationId))
            .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
            .anyMatch(log -> log.getMetadataJson().contains("KNOWLEDGE_QUERY")));
      org.junit.jupiter.api.Assertions.assertTrue(embeddingRequestCount.get() >= 2);
    } finally {
      System.clearProperty("agentx.model.QWEN_EMBED_KEY");
      server.stop(0);
    }
  }

    @Test
    void publicChatUsesChatbotSelectedEmbeddingModelInsteadOfGlobalDefault() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    AtomicInteger defaultEmbeddingRequestCount = new AtomicInteger();
    AtomicInteger selectedEmbeddingRequestCount = new AtomicInteger();
    server.createContext(
      "/default/v1/embeddings",
      exchange -> {
        defaultEmbeddingRequestCount.incrementAndGet();
        byte[] responseBody =
          """
          {
          "data":[{"embedding":[0.11,0.11,0.11]}]
          }
          """
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
      });
    server.createContext(
      "/selected/v1/embeddings",
      exchange -> {
        selectedEmbeddingRequestCount.incrementAndGet();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String vector =
          requestBody.contains("开票周期") || requestBody.contains("多久能拿到发票")
            ? "[0.99,0.02,0.01]"
            : "[0.01,0.98,0.02]";
        byte[] responseBody =
          ("""
          {
          "data":[{"embedding":%s}]
          }
          """
            .formatted(vector))
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
      });
    server.start();

    System.setProperty("agentx.model.DEFAULT_EMBED_KEY", "local-default-embed-key");
    System.setProperty("agentx.model.SELECTED_EMBED_KEY", "local-selected-embed-key");
    try {
      String defaultProviderResponse =
        mockMvc
          .perform(
            post("/api/admin/model-providers")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                "providerCode":"default-embed-openai",
                "displayName":"Default Embedding",
                "apiEndpoint":"http://127.0.0.1:%d/default/v1",
                "apiKey":"secret-default-embedding",
                "status":"ACTIVE",
                "supports":"EMBEDDING",
                "transport":"OPENAI_COMPATIBLE",
                "apiKeyEnvVar":"DEFAULT_EMBED_KEY"
                }
                """
                  .formatted(server.getAddress().getPort())))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
      long defaultProviderId = JsonTestUtils.readLong(defaultProviderResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/model-providers/{providerId}/models", defaultProviderId)
            .with(user("admin@example.com").roles("SUPER_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
              "modelCode":"text-embedding-default",
              "displayName":"Text Embedding Default",
              "purpose":"EMBEDDING",
              "status":"ACTIVE",
              "isDefault":true,
              "inputPricePer1k":0.02,
              "outputPricePer1k":0.0,
              "maxTokens":8192
              }
              """))
        .andExpect(status().isOk());

      String selectedProviderResponse =
        mockMvc
          .perform(
            post("/api/admin/model-providers")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                "providerCode":"selected-embed-openai",
                "displayName":"Selected Embedding",
                "apiEndpoint":"http://127.0.0.1:%d/selected/v1",
                "apiKey":"secret-selected-embedding",
                "status":"ACTIVE",
                "supports":"EMBEDDING",
                "transport":"OPENAI_COMPATIBLE",
                "apiKeyEnvVar":"SELECTED_EMBED_KEY"
                }
                """
                  .formatted(server.getAddress().getPort())))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
      long selectedProviderId = JsonTestUtils.readLong(selectedProviderResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/model-providers/{providerId}/models", selectedProviderId)
            .with(user("admin@example.com").roles("SUPER_ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
              "modelCode":"text-embedding-selected",
              "displayName":"Text Embedding Selected",
              "purpose":"EMBEDDING",
              "status":"ACTIVE",
              "isDefault":false,
              "inputPricePer1k":0.02,
              "outputPricePer1k":0.0,
              "maxTokens":8192
              }
              """))
        .andExpect(status().isOk());

      String tenantResponse =
        mockMvc
          .perform(
            post("/api/admin/tenants")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                "code":"tenant-selected-embedding-model",
                "name":"Tenant Selected Embedding Model",
                "contactName":"Alice",
                "contactEmail":"alice-selected-embedding@tenant.test",
                "notes":"selected embedding tenant",
                "adminEmail":"owner-selected-embedding@tenant.test",
                "adminDisplayName":"Owner Selected Embedding",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                "tenantId":%d,
                "name":"Selected Embedding Bot",
                "description":"selected embedding retrieval",
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
      String publicCode = JsonTestUtils.readText(chatbotResponse, "publicCode");

      mockMvc
        .perform(
          patch("/api/admin/chatbots/{chatbotId}/behavior", chatbotId)
            .with(user(authUser("owner-selected-embedding@tenant.test")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
              "fallbackMessage":"暂时没有标准答案。",
              "allowDirectModel":false,
              "allowFeedback":true,
              "allowHandoff":true,
              "embeddingProviderCode":"selected-embed-openai",
              "embeddingModelCode":"text-embedding-selected"
              }
              """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.embeddingProviderCode").value("selected-embed-openai"))
        .andExpect(jsonPath("$.embeddingModelCode").value("text-embedding-selected"));

      MockMultipartFile file =
        new MockMultipartFile(
          "file",
          "invoice.txt",
          "text/plain",
          "开票周期：电子发票会在 2 小时内发送到预留邮箱。".getBytes(StandardCharsets.UTF_8));

      String uploadResponse =
        mockMvc
          .perform(
            multipart("/api/admin/knowledge-sources/upload")
              .file(file)
              .with(user(authUser("owner-selected-embedding@tenant.test")))
              .param("tenantId", String.valueOf(tenantId))
              .param("chatbotId", String.valueOf(chatbotId)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
      long sourceId = JsonTestUtils.readLong(uploadResponse, "id");

      mockMvc
        .perform(
          post("/api/admin/knowledge-sources/{sourceId}/refresh", sourceId)
            .with(user(authUser("owner-selected-embedding@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
            .param("chatbotId", String.valueOf(chatbotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.embeddingProviderCode").value("selected-embed-openai"))
        .andExpect(jsonPath("$.metadata.embeddingModelCode").value("text-embedding-selected"));

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
                """
                  .formatted(publicCode)))
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
              "message":"多久能拿到发票？"
              }
              """
                .formatted(conversationId, publicCode)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("KNOWLEDGE"));

      org.junit.jupiter.api.Assertions.assertEquals(0, defaultEmbeddingRequestCount.get());
      org.junit.jupiter.api.Assertions.assertTrue(selectedEmbeddingRequestCount.get() >= 2);
      long selectedEmbeddingLogCount =
        modelCallLogRepository.findAll().stream()
          .filter(log -> log.getTenantId().equals(tenantId))
          .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
          .filter(log -> "selected-embed-openai".equals(log.getProviderCode()))
          .filter(log -> "text-embedding-selected".equals(log.getModelCode()))
          .count();
      long defaultEmbeddingLogCount =
        modelCallLogRepository.findAll().stream()
          .filter(log -> log.getTenantId().equals(tenantId))
          .filter(log -> log.getPurpose() == ModelPurpose.EMBEDDING)
          .filter(log -> "default-embed-openai".equals(log.getProviderCode()))
          .count();
      org.junit.jupiter.api.Assertions.assertEquals(2L, selectedEmbeddingLogCount);
      org.junit.jupiter.api.Assertions.assertEquals(0L, defaultEmbeddingLogCount);
    } finally {
      System.clearProperty("agentx.model.DEFAULT_EMBED_KEY");
      System.clearProperty("agentx.model.SELECTED_EMBED_KEY");
      server.stop(0);
    }
    }

      @Test
      void publicChatEnforcesConversationAndMessageQuota() throws Exception {
      String planResponse =
        mockMvc
          .perform(
            post("/api/admin/plans")
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "code":"public-chat-limit",
                  "name":"Public Chat Limit",
                  "limits":{"chatbots":2,"conversations":1,"messages":2,"tokens":100}
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
                  "code":"tenant-chat-limit",
                  "name":"Tenant Chat Limit",
                  "contactName":"Alice",
                  "contactEmail":"alice-limit@tenant.test",
                  "notes":"chat tenant limit",
                  "adminEmail":"owner-chat-limit@tenant.test",
                  "adminDisplayName":"Owner Chat Limit",
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
              .with(user("admin@example.com").roles("SUPER_ADMIN"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                """
                {
                  "tenantId":%d,
                  "name":"Support Bot",
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
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONVERSATIONS_LIMIT_REACHED"));

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
        .perform(
          post("/api/public/chat/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "conversationId":%d,
                "chatbotPublicCode":"%s",
                "language":"zh-CN",
                "message":"再问一次"
              }
              """.formatted(conversationId, publicCode)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSAGES_LIMIT_REACHED"));
      }
}
