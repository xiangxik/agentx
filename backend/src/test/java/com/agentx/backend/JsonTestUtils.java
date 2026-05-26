package com.agentx.backend;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class JsonTestUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonTestUtils() {}

  public static long readLong(String json, String field) throws Exception {
    JsonNode node = OBJECT_MAPPER.readTree(json);
    return node.get(field).asLong();
  }

  public static String readText(String json, String field) throws Exception {
    JsonNode node = OBJECT_MAPPER.readTree(json);
    return node.get(field).asText();
  }
}
