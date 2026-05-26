package com.agentx.backend.knowledge.application;

import javax.sql.DataSource;
import java.util.Locale;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChunkVectorStore {

  private final JdbcTemplate jdbcTemplate;

  public KnowledgeChunkVectorStore(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public void syncChunkVector(Long chunkId, String embeddingJson) {
    if (chunkId == null || embeddingJson == null || embeddingJson.isBlank() || !isPgvectorReady()) {
      return;
    }

    try {
      jdbcTemplate.update(
          "update knowledge_chunk set embedding_vector = cast(? as vector) where id = ?",
          embeddingJson,
          chunkId);
    } catch (RuntimeException exception) {
      // Fall back to embedding_json-only retrieval when pgvector is unavailable or misconfigured.
    }
  }

  public boolean isPgvectorReady() {
    return isPostgreSql() && hasPgvectorExtension() && hasEmbeddingVectorColumn();
  }

  private boolean isPostgreSql() {
    try {
      return Boolean.TRUE.equals(
          jdbcTemplate.execute(
              (ConnectionCallback<Boolean>)
                  connection ->
                      connection
                          .getMetaData()
                          .getDatabaseProductName()
                          .toLowerCase(Locale.ROOT)
                          .contains("postgres")));
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean hasPgvectorExtension() {
    try {
      Boolean enabled =
          jdbcTemplate.queryForObject(
              "select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class);
      return Boolean.TRUE.equals(enabled);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean hasEmbeddingVectorColumn() {
    try {
      Boolean enabled =
          jdbcTemplate.queryForObject(
              """
              select exists(
                select 1
                from information_schema.columns
                where table_name = 'knowledge_chunk'
                  and column_name = 'embedding_vector'
              )
              """,
              Boolean.class);
      return Boolean.TRUE.equals(enabled);
    } catch (RuntimeException exception) {
      return false;
    }
  }
}