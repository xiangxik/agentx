package com.agentx.backend.knowledge.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.chatbot.domain.ChatbotRepository;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.knowledge.domain.KnowledgeSource;
import com.agentx.backend.knowledge.domain.KnowledgeChunk;
import com.agentx.backend.knowledge.domain.KnowledgeChunkRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.agentx.backend.knowledge.domain.KnowledgeSourceType;
import com.agentx.backend.plan.application.PlanService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeSourceService {

  private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private static final Set<String> SUPPORTED_FILE_EXTENSIONS =
      Set.of("txt", "md", "pdf", "docx", "csv", "json");

  private final KnowledgeSourceRepository knowledgeSourceRepository;
  private final KnowledgeChunkRepository knowledgeChunkRepository;
  private final ChatbotRepository chatbotRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;
  private final PlanService planService;

  public KnowledgeSourceService(
      KnowledgeSourceRepository knowledgeSourceRepository,
      KnowledgeChunkRepository knowledgeChunkRepository,
      ChatbotRepository chatbotRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper,
      PlanService planService) {
    this.knowledgeSourceRepository = knowledgeSourceRepository;
    this.knowledgeChunkRepository = knowledgeChunkRepository;
    this.chatbotRepository = chatbotRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
    this.planService = planService;
  }

  @Transactional(readOnly = true)
  public List<KnowledgeSourceSummary> list(CurrentUser actor, Long tenantId, Long chatbotId) {
    validateChatbotAccess(actor, tenantId, chatbotId);
    return knowledgeSourceRepository
        .findByTenantIdAndChatbotIdAndStatusNotOrderByIdDesc(
            tenantId, chatbotId, KnowledgeSourceStatus.DELETED)
        .stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public KnowledgeSourceDetail get(CurrentUser actor, Long tenantId, Long chatbotId, Long sourceId) {
    validateChatbotAccess(actor, tenantId, chatbotId);
    KnowledgeSource source =
        knowledgeSourceRepository
            .findByIdAndTenantIdAndChatbotIdAndStatusNot(
                sourceId, tenantId, chatbotId, KnowledgeSourceStatus.DELETED)
            .orElseThrow(() -> new IllegalArgumentException("KNOWLEDGE_SOURCE_NOT_FOUND"));
    return toDetail(source);
  }

  @Transactional
  public KnowledgeSourceSummary upload(
      CurrentUser actor, Long tenantId, Long chatbotId, MultipartFile file) {
    validateChatbotAccess(actor, tenantId, chatbotId);

    if (file.isEmpty()) {
      throw new IllegalArgumentException("FILE_REQUIRED");
    }

    String originalFileName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
    validateFileUpload(originalFileName, file.getSize());

    long fileSizeBytes = file.getSize();
    long storageUsageMb = Math.max(1L, (long) Math.ceil((double) fileSizeBytes / (1024D * 1024D)));
    planService.ensureTenantWithinLimit(tenantId, "files", 1);
    planService.ensureTenantWithinLimit(tenantId, "storageMb", storageUsageMb);

    try {
      Path storageDirectory = resolveStorageRoot().resolve("tenant-" + tenantId).resolve("chatbot-" + chatbotId);
      Files.createDirectories(storageDirectory);

      String sanitizedFileName = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
      Path storedFilePath = storageDirectory.resolve(UUID.randomUUID() + "-" + sanitizedFileName);
      file.transferTo(storedFilePath);

      KnowledgeSource knowledgeSource = new KnowledgeSource();
      knowledgeSource.setTenantId(tenantId);
      knowledgeSource.setChatbotId(chatbotId);
      knowledgeSource.setSourceType(KnowledgeSourceType.FILE);
      knowledgeSource.setStatus(KnowledgeSourceStatus.UPLOADED);
      knowledgeSource.setSourceName(originalFileName);
      knowledgeSource.setSourceUri(storedFilePath.toString());
      knowledgeSource.setMetadataJson(
          toJson(
              Map.of(
                  "contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                  "fileSizeBytes", fileSizeBytes,
                  "storagePath", storedFilePath.toString())));
      KnowledgeSource savedSource = knowledgeSourceRepository.save(knowledgeSource);

      auditLogService.record(
          tenantId,
          actor.userId(),
          "KNOWLEDGE_FILE_UPLOADED",
          "KNOWLEDGE_SOURCE",
          String.valueOf(savedSource.getId()),
          "SUCCESS",
          "MEDIUM",
          Map.of("chatbotId", chatbotId, "fileSizeBytes", fileSizeBytes));

      return toSummary(savedSource);
    } catch (IOException exception) {
      throw new IllegalStateException("KNOWLEDGE_FILE_UPLOAD_FAILED", exception);
    }
  }

  @Transactional
  public KnowledgeSourceSummary createWebSource(
      CurrentUser actor, Long tenantId, Long chatbotId, CreateWebSourceRequest request) {
    validateChatbotAccess(actor, tenantId, chatbotId);

    String sourceUrl = request.url() == null ? "" : request.url().trim();
    if (sourceUrl.isBlank()) {
      throw new IllegalArgumentException("URL_REQUIRED");
    }

    String normalizedUrl = validateAndNormalizeUrl(sourceUrl);
    String sourceName =
        request.name() == null || request.name().trim().isBlank()
            ? normalizedUrl
            : request.name().trim();

    KnowledgeSource knowledgeSource = new KnowledgeSource();
    knowledgeSource.setTenantId(tenantId);
    knowledgeSource.setChatbotId(chatbotId);
    knowledgeSource.setSourceType(KnowledgeSourceType.WEB);
    knowledgeSource.setStatus(KnowledgeSourceStatus.UPLOADED);
    knowledgeSource.setSourceName(sourceName);
    knowledgeSource.setSourceUri(normalizedUrl);
    knowledgeSource.setMetadataJson(toJson(Map.of("url", normalizedUrl)));

    KnowledgeSource savedSource = knowledgeSourceRepository.save(knowledgeSource);

    auditLogService.record(
        tenantId,
        actor.userId(),
        "KNOWLEDGE_WEB_SOURCE_CREATED",
        "KNOWLEDGE_SOURCE",
        String.valueOf(savedSource.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("chatbotId", chatbotId, "url", normalizedUrl));

    return toSummary(savedSource);
  }

  @Transactional
  public KnowledgeSourceDetail refresh(CurrentUser actor, Long tenantId, Long chatbotId, Long sourceId) {
    KnowledgeSource source = requireSource(actor, tenantId, chatbotId, sourceId);
    Map<String, Object> metadata = readMetadata(source);
    Instant now = Instant.now();

    if (source.getSourceType() == KnowledgeSourceType.WEB) {
      metadata.put("lastFetchedAt", now.toString());
    } else {
      metadata.put("lastProcessedAt", now.toString());
    }

    metadata.put("lastRefreshAt", now.toString());
    metadata.put("lastRefreshResult", "SUCCESS");

    source.setStatus(KnowledgeSourceStatus.ACTIVE);
    source.setFailureReason(null);
    source.setUpdatedAt(now);
    rebuildChunks(source, metadata);
    source.setMetadataJson(toJson(metadata));

    KnowledgeSource savedSource = knowledgeSourceRepository.save(source);

    auditLogService.record(
        tenantId,
        actor.userId(),
        "KNOWLEDGE_SOURCE_REFRESHED",
        "KNOWLEDGE_SOURCE",
        String.valueOf(savedSource.getId()),
        "SUCCESS",
        "LOW",
        Map.of("chatbotId", chatbotId, "sourceType", savedSource.getSourceType().name()));

    return toDetail(savedSource);
  }

  @Transactional
  public KnowledgeSourceDetail updateStatus(
      CurrentUser actor,
      Long tenantId,
      Long chatbotId,
      Long sourceId,
      KnowledgeSourceStatus status) {
    KnowledgeSource source = requireSource(actor, tenantId, chatbotId, sourceId);
    source.setStatus(status);
    source.setUpdatedAt(Instant.now());
    KnowledgeSource savedSource = knowledgeSourceRepository.save(source);

    auditLogService.record(
        tenantId,
        actor.userId(),
        "KNOWLEDGE_SOURCE_STATUS_UPDATED",
        "KNOWLEDGE_SOURCE",
        String.valueOf(savedSource.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("chatbotId", chatbotId, "status", status.name()));

    return toDetail(savedSource);
  }

  @Transactional
  public KnowledgeSourceDetail delete(CurrentUser actor, Long tenantId, Long chatbotId, Long sourceId) {
    KnowledgeSource source = requireSource(actor, tenantId, chatbotId, sourceId);
    Map<String, Object> metadata = readMetadata(source);
    Object storagePath = metadata.get("storagePath");

    if (storagePath != null) {
      try {
        Files.deleteIfExists(Path.of(String.valueOf(storagePath)));
      } catch (IOException exception) {
        throw new IllegalStateException("KNOWLEDGE_FILE_DELETE_FAILED", exception);
      }
    }

    knowledgeChunkRepository.deleteByKnowledgeSourceId(source.getId());
    source.setStatus(KnowledgeSourceStatus.DELETED);
    source.setUpdatedAt(Instant.now());
    KnowledgeSource savedSource = knowledgeSourceRepository.save(source);

    auditLogService.record(
        tenantId,
        actor.userId(),
        "KNOWLEDGE_SOURCE_DELETED",
        "KNOWLEDGE_SOURCE",
        String.valueOf(savedSource.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("chatbotId", chatbotId, "status", KnowledgeSourceStatus.DELETED.name()));

    return toDetail(savedSource);
  }

  @Transactional
  public KnowledgeSourceDetail retry(CurrentUser actor, Long tenantId, Long chatbotId, Long sourceId) {
    KnowledgeSource source = requireSource(actor, tenantId, chatbotId, sourceId);
    Map<String, Object> metadata = readMetadata(source);
    Instant now = Instant.now();

    metadata.put("lastRetryAt", now.toString());
    metadata.put("retryCount", extractLong(metadata.get("retryCount")) + 1);

    if (source.getSourceType() == KnowledgeSourceType.WEB) {
      metadata.put("lastFetchedAt", now.toString());
    } else {
      metadata.put("lastProcessedAt", now.toString());
    }

    source.setStatus(KnowledgeSourceStatus.ACTIVE);
    source.setFailureReason(null);
    source.setUpdatedAt(now);
    rebuildChunks(source, metadata);
    source.setMetadataJson(toJson(metadata));

    KnowledgeSource savedSource = knowledgeSourceRepository.save(source);

    auditLogService.record(
        tenantId,
        actor.userId(),
        "KNOWLEDGE_SOURCE_RETRIED",
        "KNOWLEDGE_SOURCE",
        String.valueOf(savedSource.getId()),
        "SUCCESS",
        "MEDIUM",
        Map.of("chatbotId", chatbotId, "sourceType", savedSource.getSourceType().name()));

    return toDetail(savedSource);
  }

  private void validateChatbotAccess(CurrentUser actor, Long tenantId, Long chatbotId) {
    boolean allowed =
        actor.isSuperAdmin()
            ? chatbotRepository.findByIdAndTenantId(chatbotId, tenantId).isPresent()
            : actor.tenantId() != null
                && actor.tenantId().equals(tenantId)
                && chatbotRepository.findByIdAndTenantId(chatbotId, actor.tenantId()).isPresent();

    if (!allowed) {
      throw new IllegalArgumentException("CHATBOT_NOT_FOUND");
    }
  }

  private KnowledgeSource requireSource(CurrentUser actor, Long tenantId, Long chatbotId, Long sourceId) {
    validateChatbotAccess(actor, tenantId, chatbotId);
    return knowledgeSourceRepository
        .findByIdAndTenantIdAndChatbotIdAndStatusNot(
            sourceId, tenantId, chatbotId, KnowledgeSourceStatus.DELETED)
        .orElseThrow(() -> new IllegalArgumentException("KNOWLEDGE_SOURCE_NOT_FOUND"));
  }

  private Path resolveStorageRoot() {
    Path current = Paths.get(System.getProperty("user.dir"));
    if (Files.exists(current.resolve("pom.xml"))) {
      return current.resolve("storage").resolve("knowledge");
    }
    return current.resolve("backend").resolve("storage").resolve("knowledge");
  }

  private String validateAndNormalizeUrl(String sourceUrl) {
    try {
      URI uri = new URI(sourceUrl);
      String scheme = uri.getScheme();
      if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        throw new IllegalArgumentException("INVALID_SOURCE_URL");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("INVALID_SOURCE_URL");
      }
      return uri.toString();
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("INVALID_SOURCE_URL");
    }
  }

  private void validateFileUpload(String fileName, long fileSizeBytes) {
    String extension = extractExtension(fileName);
    if (!SUPPORTED_FILE_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException("FILE_TYPE_NOT_SUPPORTED");
    }

    if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
      throw new IllegalArgumentException("FILE_SIZE_LIMIT_EXCEEDED");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMetadata(KnowledgeSource source) {
    try {
      return new LinkedHashMap<>(objectMapper.readValue(source.getMetadataJson(), Map.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize knowledge source metadata", exception);
    }
  }

  private long extractLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return 0L;
  }

  private void rebuildChunks(KnowledgeSource source, Map<String, Object> metadata) {
    knowledgeChunkRepository.deleteByKnowledgeSourceId(source.getId());
    List<String> chunks = buildChunks(source, metadata);
    for (int index = 0; index < chunks.size(); index++) {
      String content = chunks.get(index);
      KnowledgeChunk chunk = new KnowledgeChunk();
      chunk.setTenantId(source.getTenantId());
      chunk.setChatbotId(source.getChatbotId());
      chunk.setKnowledgeSourceId(source.getId());
      chunk.setChunkIndex(index);
      chunk.setContent(content);
      chunk.setSummary(content.length() > 120 ? content.substring(0, 120) : content);
      chunk.setSourceLink(source.getSourceUri());
      knowledgeChunkRepository.save(chunk);
    }
    metadata.put("chunkCount", chunks.size());
  }

  private List<String> buildChunks(KnowledgeSource source, Map<String, Object> metadata) {
    String content =
        source.getSourceType() == KnowledgeSourceType.FILE
            ? extractFileContent(source, metadata)
            : extractWebContent(source, metadata);

    String normalized = content == null ? "" : content.trim();
    if (normalized.isBlank()) {
      normalized = source.getSourceName();
    }

    java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
    int chunkSize = 320;
    for (int start = 0; start < normalized.length(); start += chunkSize) {
      int end = Math.min(normalized.length(), start + chunkSize);
      chunks.add(normalized.substring(start, end));
    }

    if (chunks.isEmpty()) {
      chunks.add(normalized);
    }
    return chunks;
  }

  private String extractFileContent(KnowledgeSource source, Map<String, Object> metadata) {
    Object storagePath = metadata.get("storagePath");
    if (storagePath == null) {
      return source.getSourceName();
    }

    Path path = Path.of(String.valueOf(storagePath));
    if (!Files.exists(path)) {
      return source.getSourceName();
    }

    String extension = extractExtension(source.getSourceName());
    try {
      if (Set.of("txt", "md", "csv", "json").contains(extension)) {
        return Files.readString(path, StandardCharsets.UTF_8);
      }
      return "文件名称：" + source.getSourceName() + "。文件类型：" + metadata.getOrDefault("contentType", extension);
    } catch (IOException exception) {
      throw new IllegalStateException("KNOWLEDGE_CHUNK_BUILD_FAILED", exception);
    }
  }

  private String extractWebContent(KnowledgeSource source, Map<String, Object> metadata) {
    String sourceUrl = String.valueOf(metadata.getOrDefault("url", source.getSourceUri()));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(sourceUrl))
            .timeout(Duration.ofSeconds(5))
            .header("User-Agent", "AgentX-KnowledgeFetcher/0.1")
            .GET()
            .build();

    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "KNOWLEDGE_CHUNK_BUILD_FAILED",
            new IOException("Unexpected HTTP status: " + response.statusCode()));
      }

      String html = response.body() == null ? "" : response.body();
      String title = extractHtmlTitle(html);
      String content = extractHtmlText(html);

      metadata.put("lastFetchedUrl", response.uri().toString());
      metadata.put("lastFetchedStatus", response.statusCode());
      if (!title.isBlank()) {
        metadata.put("pageTitle", title);
      }

      String normalized = content.trim();
      if (normalized.isBlank()) {
        return "网页来源：%s。链接：%s。".formatted(source.getSourceName(), sourceUrl);
      }

      return title.isBlank() ? normalized : title + "\n" + normalized;
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("KNOWLEDGE_CHUNK_BUILD_FAILED", exception);
    }
  }

  private String extractHtmlTitle(String html) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
    return matcher.find() ? cleanHtmlText(matcher.group(1)) : "";
  }

  private String extractHtmlText(String html) {
    return cleanHtmlText(
        html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n")
            .replaceAll("(?i)</div>", "\n")
            .replaceAll("(?is)<[^>]+>", " "));
  }

  private String cleanHtmlText(String value) {
    return value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String extractExtension(String fileName) {
    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
  }

  @SuppressWarnings("unchecked")
  private KnowledgeSourceSummary toSummary(KnowledgeSource source) {
    try {
      Map<String, Object> metadata = objectMapper.readValue(source.getMetadataJson(), Map.class);
      return new KnowledgeSourceSummary(
          source.getId(),
          source.getTenantId(),
          source.getChatbotId(),
          source.getSourceType().name(),
          source.getStatus().name(),
          source.getSourceName(),
          source.getSourceUri(),
          metadata.get("contentType") == null ? null : String.valueOf(metadata.get("contentType")),
          metadata.get("fileSizeBytes") == null
              ? 0L
              : ((Number) metadata.get("fileSizeBytes")).longValue(),
          source.getCreatedAt().toString());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize knowledge source metadata", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private KnowledgeSourceDetail toDetail(KnowledgeSource source) {
    try {
      Map<String, Object> metadata = objectMapper.readValue(source.getMetadataJson(), Map.class);
      Map<String, String> normalizedMetadata = new LinkedHashMap<>();
      metadata.forEach((key, value) -> normalizedMetadata.put(key, value == null ? "" : String.valueOf(value)));
      List<KnowledgeChunkPreview> chunks =
        knowledgeChunkRepository.findByKnowledgeSourceIdOrderByChunkIndexAsc(source.getId()).stream()
          .map(
            chunk ->
              new KnowledgeChunkPreview(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getSummary(),
                chunk.getContent(),
                chunk.getSourceLink()))
          .toList();
      return new KnowledgeSourceDetail(
          source.getId(),
          source.getTenantId(),
          source.getChatbotId(),
          source.getSourceType().name(),
          source.getStatus().name(),
          source.getSourceName(),
          source.getSourceUri(),
          source.getFailureReason(),
          normalizedMetadata,
          chunks,
          source.getCreatedAt().toString());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize knowledge source metadata", exception);
    }
  }

  private String toJson(Map<String, Object> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize knowledge source metadata", exception);
    }
  }

  public record KnowledgeSourceSummary(
      Long id,
      Long tenantId,
      Long chatbotId,
      String sourceType,
      String status,
      String sourceName,
      String sourceUri,
      String contentType,
      long fileSizeBytes,
      String createdAt) {}

  public record KnowledgeSourceDetail(
      Long id,
      Long tenantId,
      Long chatbotId,
      String sourceType,
      String status,
      String sourceName,
      String sourceUri,
      String failureReason,
      Map<String, String> metadata,
        List<KnowledgeChunkPreview> chunks,
      String createdAt) {}

      public record KnowledgeChunkPreview(
        Long id, Integer chunkIndex, String summary, String content, String sourceLink) {}

  public record CreateWebSourceRequest(String name, String url) {}
}