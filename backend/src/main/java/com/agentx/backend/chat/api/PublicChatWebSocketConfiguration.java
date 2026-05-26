package com.agentx.backend.chat.api;

import java.util.Objects;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PublicChatWebSocketConfiguration implements WebSocketConfigurer {

  private final PublicChatWebSocketHandler publicChatWebSocketHandler;

  public PublicChatWebSocketConfiguration(PublicChatWebSocketHandler publicChatWebSocketHandler) {
    this.publicChatWebSocketHandler = publicChatWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
    registry
        .addHandler(Objects.requireNonNull(publicChatWebSocketHandler), "/ws/public/chat")
        .setAllowedOrigins(
            "http://localhost:4173",
            "http://127.0.0.1:4173",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4174",
            "http://127.0.0.1:4174",
            "http://localhost:5174",
            "http://127.0.0.1:5174");
  }
}