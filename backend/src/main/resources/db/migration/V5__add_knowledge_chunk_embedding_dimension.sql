ALTER TABLE knowledge_chunk
ADD COLUMN IF NOT EXISTS embedding_dimension INTEGER;

UPDATE knowledge_chunk
SET embedding_dimension = jsonb_array_length(embedding_json::jsonb)
WHERE embedding_json IS NOT NULL
  AND embedding_json <> ''
  AND (embedding_dimension IS NULL OR embedding_dimension = 0);