package com.agentx.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.application.BootstrapDataInitializer;
import org.junit.jupiter.api.BeforeEach;
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
class PublicChatFlowTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private BootstrapDataInitializer bootstrapDataInitializer;

  @BeforeEach
  void setUp() {
    bootstrapDataInitializer.ensureRole("TENANT_ADMIN", "租户管理员");
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
}
