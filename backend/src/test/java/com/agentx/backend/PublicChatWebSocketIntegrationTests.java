package com.agentx.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentx.backend.auth.application.DatabaseUserDetailsService;
import com.sun.net.httpserver.HttpServer;
import com.agentx.backend.auth.application.BootstrapDataInitializer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = "agentx.files.root=/tmp/agentx-test-files")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SuppressWarnings("null")
class PublicChatWebSocketIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private BootstrapDataInitializer bootstrapDataInitializer;

  @Autowired private DatabaseUserDetailsService userDetailsService;

  @Autowired private ObjectMapper objectMapper;

  @LocalServerPort private int port;

  @BeforeEach
  void setUp() {
    bootstrapDataInitializer.ensureRole("TENANT_ADMIN", "租户管理员");
  }

  private UserDetails authUser(String email) {
    return userDetailsService.loadUserByUsername(email);
  }

  @Test
  void websocketMessageReturnsFaqAnswer() throws Exception {
    String tenantResponse =
        mockMvc
            .perform(
                post("/api/admin/tenants")
                    .with(user("admin@example.com").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "code":"tenant-websocket-chat",
                          "name":"Tenant WebSocket Chat",
                          "contactName":"Alice",
                          "contactEmail":"alice-websocket@tenant.test",
                          "notes":"websocket chat tenant",
                          "adminEmail":"owner-websocket-chat@tenant.test",
                          "adminDisplayName":"Owner WebSocket Chat",
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
                          "name":"WebSocket Support Bot",
                          "description":"websocket support",
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
                      "question":"怎么联系客服？",
                      "alternateQuestions":["如何联系人工客服"],
                      "answer":"你可以在工作日 9:00-18:00 联系在线客服。"
                    }
                    """
                        .formatted(tenantId, chatbotId)))
        .andExpect(status().isOk());

    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest initRequest =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:%d/api/public/chat/init".formatted(port)))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(
                HttpRequest.BodyPublishers.ofString(
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
            .build();

    HttpResponse<String> initResponse =
        httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, initResponse.statusCode());

    JsonNode initPayload = objectMapper.readTree(initResponse.body());
    long conversationId = initPayload.get("conversationId").asLong();
    assertTrue(conversationId > 0);

    WebSocketEventListener listener = new WebSocketEventListener(objectMapper);
    WebSocket webSocket =
        httpClient
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(
                URI.create(
                    "ws://127.0.0.1:%d/ws/public/chat?chatbotPublicCode=%s&conversationId=%d"
                        .formatted(port, publicCode, conversationId)),
                listener)
            .get(10, TimeUnit.SECONDS);

    JsonNode connectedEvent = listener.connectedEvent().get(10, TimeUnit.SECONDS);
    assertEquals("CONNECTED", connectedEvent.get("type").asText());

    webSocket
        .sendText(
            objectMapper.writeValueAsString(
                objectMapper
                    .createObjectNode()
                    .put("conversationId", conversationId)
                    .put("clientMessageId", "ws-test-1")
                    .put("language", "zh-CN")
                    .put("message", "怎么联系客服？")),
            true)
        .get(10, TimeUnit.SECONDS);

    JsonNode completedEvent = listener.completedEvent().get(10, TimeUnit.SECONDS);
    assertEquals("MESSAGE_COMPLETED", completedEvent.get("type").asText());
    assertEquals("FAQ", completedEvent.get("sourceType").asText());
    assertEquals("你可以在工作日 9:00-18:00 联系在线客服。", completedEvent.get("answer").asText());
    assertEquals("FAQ", completedEvent.get("citations").get(0).get("sourceType").asText());
    assertFalse(completedEvent.get("citations").isEmpty());
    assertNotNull(completedEvent.get("assistantMessageId"));

    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
  }

    @Test
    void websocketMessageReturnsKnowledgeAnswer() throws Exception {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext(
          "/invoice",
          exchange -> {
            byte[] responseBody =
                """
                <html>
                  <body>
                    <article>
                      <h1>电子发票帮助</h1>
                      <p>企业用户可以在订单中心提交电子发票申请。</p>
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
                "code":"tenant-websocket-knowledge",
                "name":"Tenant WebSocket Knowledge",
                "contactName":"Alice",
                "contactEmail":"alice-websocket-knowledge@tenant.test",
                "notes":"websocket knowledge tenant",
                "adminEmail":"owner-websocket-knowledge@tenant.test",
                "adminDisplayName":"Owner WebSocket Knowledge",
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
                "name":"WebSocket Knowledge Bot",
                "description":"websocket knowledge",
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
          .with(user(authUser("owner-websocket-knowledge@tenant.test")))
            .param("tenantId", String.valueOf(tenantId))
          .param("chatbotId", String.valueOf(chatbotId))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "url":"http://127.0.0.1:%d/invoice",
              "name":"Invoice Help"
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
          .with(user(authUser("owner-websocket-knowledge@tenant.test")))
          .param("tenantId", String.valueOf(tenantId))
          .param("chatbotId", String.valueOf(chatbotId)))
      .andExpect(status().isOk());

    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest initRequest =
      HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:%d/api/public/chat/init".formatted(port)))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10))
        .POST(
          HttpRequest.BodyPublishers.ofString(
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
        .build();

    HttpResponse<String> initResponse =
      httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, initResponse.statusCode());

    JsonNode initPayload = objectMapper.readTree(initResponse.body());
    long conversationId = initPayload.get("conversationId").asLong();
    assertTrue(conversationId > 0);

    WebSocketEventListener listener = new WebSocketEventListener(objectMapper);
    WebSocket webSocket =
      httpClient
        .newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(
          URI.create(
            "ws://127.0.0.1:%d/ws/public/chat?chatbotPublicCode=%s&conversationId=%d"
              .formatted(port, publicCode, conversationId)),
          listener)
        .get(10, TimeUnit.SECONDS);

    JsonNode connectedEvent = listener.connectedEvent().get(10, TimeUnit.SECONDS);
    assertEquals("CONNECTED", connectedEvent.get("type").asText());

    webSocket
      .sendText(
        objectMapper.writeValueAsString(
          objectMapper
            .createObjectNode()
            .put("conversationId", conversationId)
            .put("clientMessageId", "ws-knowledge-1")
            .put("language", "zh-CN")
            .put("message", "怎么申请电子发票？")),
        true)
      .get(10, TimeUnit.SECONDS);

    JsonNode completedEvent = listener.completedEvent().get(10, TimeUnit.SECONDS);
    assertEquals("MESSAGE_COMPLETED", completedEvent.get("type").asText());
    assertEquals("KNOWLEDGE", completedEvent.get("sourceType").asText());
    assertTrue(completedEvent.get("answer").asText().contains("电子发票申请"));
    assertEquals("KNOWLEDGE", completedEvent.get("citations").get(0).get("sourceType").asText());
    assertFalse(completedEvent.get("citations").isEmpty());
    assertNotNull(completedEvent.get("assistantMessageId"));

    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
    } finally {
      server.stop(0);
    }
    }

  private static final class WebSocketEventListener implements WebSocket.Listener {

    private final ObjectMapper objectMapper;
    private final StringBuilder textBuffer = new StringBuilder();
    private final CompletableFuture<JsonNode> connectedEvent = new CompletableFuture<>();
    private final CompletableFuture<JsonNode> completedEvent = new CompletableFuture<>();

    private WebSocketEventListener(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    CompletableFuture<JsonNode> connectedEvent() {
      return connectedEvent;
    }

    CompletableFuture<JsonNode> completedEvent() {
      return completedEvent;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
      WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      textBuffer.append(data);

      if (last) {
        try {
          JsonNode payload = objectMapper.readTree(textBuffer.toString());
          textBuffer.setLength(0);

          if ("CONNECTED".equals(payload.get("type").asText())) {
            connectedEvent.complete(payload);
          }

          if ("MESSAGE_COMPLETED".equals(payload.get("type").asText())) {
            completedEvent.complete(payload);
          }
        } catch (Exception exception) {
          connectedEvent.completeExceptionally(exception);
          completedEvent.completeExceptionally(exception);
        }
      }

      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      connectedEvent.completeExceptionally(error);
      completedEvent.completeExceptionally(error);
    }
  }
}