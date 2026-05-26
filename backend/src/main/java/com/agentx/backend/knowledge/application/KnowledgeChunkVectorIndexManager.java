package com.agentx.backend.knowledge.application;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeChunkVectorIndexManager {

  private final JdbcTemplate jdbcTemplate;
  private final KnowledgeChunkVectorStore knowledgeChunkVectorStore;
  private final ConcurrentMap<Integer, Boolean> ensuredDimensions = new ConcurrentHashMap<>();

  public KnowledgeChunkVectorIndexManager(
      DataSource dataSource, KnowledgeChunkVectorStore knowledgeChunkVectorStore) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.knowledgeChunkVectorStore = knowledgeChunkVectorStore;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureExistingIndexes() {
    if (!knowledgeChunkVectorStore.isPgvectorReady()) {
      return;
    }

    List<Integer> dimensions =
        jdbcTemplate.query(
            """
            select distinct embedding_dimension
            from knowledge_chunk
            where embedding_dimension is not null
              and embedding_dimension > 0
              and embedding_vector is not null
            order by embedding_dimension asc
            """,
            (resultSet, rowNum) -> resultSet.getInt(1));
    dimensions.forEach(this::ensureDimensionIndex);
  }

  public void ensureDimensionIndex(Integer dimension) {
    if (dimension == null || dimension <= 0 || !knowledgeChunkVectorStore.isPgvectorReady()) {
      return;
    }
    if (ensuredDimensions.putIfAbsent(dimension, Boolean.TRUE) != null) {
      return;
    }

    try {
      jdbcTemplate.execute(buildHnswIndexSql(dimension));
    } catch (RuntimeException exception) {
      ensuredDimensions.remove(dimension);
    }
  }

  private String buildHnswIndexSql(int dimension) {
    return """
        create index if not exists %s
        on knowledge_chunk
        using hnsw ((embedding_vector::vector(%d)) vector_cosine_ops)
        where embedding_vector is not null
          and embedding_dimension = %d
        """
        .formatted(indexNameFor(dimension), dimension, dimension);
  }

  private String indexNameFor(int dimension) {
    return ("idx_kc_embedding_hnsw_" + dimension).toLowerCase(Locale.ROOT);
  }
}