package com.agentx.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.application.DatabaseUserDetailsService;
import com.agentx.backend.auth.application.BootstrapDataInitializer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
