CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_scope_dimension
ON knowledge_chunk (tenant_id, chatbot_id, embedding_dimension);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_source_chunk
ON knowledge_chunk (knowledge_source_id, chunk_index);