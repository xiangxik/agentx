DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'code'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'provider_code'
    ) THEN
        ALTER TABLE model_provider RENAME COLUMN code TO provider_code;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'name'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'display_name'
    ) THEN
        ALTER TABLE model_provider RENAME COLUMN name TO display_name;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'base_url'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'api_endpoint'
    ) THEN
        ALTER TABLE model_provider RENAME COLUMN base_url TO api_endpoint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'encrypted_api_key'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'api_key_hint'
    ) THEN
        ALTER TABLE model_provider RENAME COLUMN encrypted_api_key TO api_key_hint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'config_json'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'model_provider' AND column_name = 'metadata_json'
    ) THEN
        ALTER TABLE model_provider RENAME COLUMN config_json TO metadata_json;
    END IF;
END $$;

ALTER TABLE model_provider ADD COLUMN IF NOT EXISTS provider_code VARCHAR(64);
ALTER TABLE model_provider ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE model_provider ADD COLUMN IF NOT EXISTS api_endpoint TEXT;
ALTER TABLE model_provider ADD COLUMN IF NOT EXISTS api_key_hint TEXT;
ALTER TABLE model_provider ADD COLUMN IF NOT EXISTS metadata_json TEXT;

UPDATE model_provider
SET provider_code = COALESCE(provider_code, 'provider-' || id),
    display_name = COALESCE(display_name, provider_code, 'Provider ' || id),
    metadata_json = COALESCE(metadata_json, '{}');

ALTER TABLE model_provider ALTER COLUMN provider_code SET NOT NULL;
ALTER TABLE model_provider ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE model_provider ALTER COLUMN api_endpoint DROP NOT NULL;
ALTER TABLE model_provider ALTER COLUMN metadata_json SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_provider_provider_code
    ON model_provider (provider_code);

CREATE TABLE IF NOT EXISTS model_definition (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    model_code VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    input_price_per_1k DOUBLE PRECISION,
    output_price_per_1k DOUBLE PRECISION,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_definition_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_definition_provider_model_code
    ON model_definition (provider_id, model_code);

ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS assistant_message_id BIGINT;
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS provider_code VARCHAR(64);
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS model_code VARCHAR(255);
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS purpose VARCHAR(32);
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS latency_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE model_call_log ADD COLUMN IF NOT EXISTS metadata_json TEXT NOT NULL DEFAULT '{}';

UPDATE model_call_log log
SET provider_code = COALESCE(
        log.provider_code,
        (SELECT provider.provider_code FROM model_provider provider WHERE provider.id = log.provider_id),
        'AGENTX_BUILTIN'),
    model_code = COALESCE(log.model_code, log.model_name, 'unknown-model'),
    purpose = COALESCE(
        log.purpose,
        CASE
            WHEN log.usage_type = 'EMBEDDING' THEN 'EMBEDDING'
            ELSE 'CHAT_COMPLETION'
        END),
    metadata_json = COALESCE(log.metadata_json, '{}');

ALTER TABLE model_call_log ALTER COLUMN provider_code SET NOT NULL;
ALTER TABLE model_call_log ALTER COLUMN model_code SET NOT NULL;
ALTER TABLE model_call_log ALTER COLUMN purpose SET NOT NULL;
ALTER TABLE model_call_log ALTER COLUMN usage_type DROP NOT NULL;
ALTER TABLE model_call_log ALTER COLUMN model_name DROP NOT NULL;
ALTER TABLE model_call_log ALTER COLUMN prompt_tokens TYPE INTEGER USING prompt_tokens::integer;
ALTER TABLE model_call_log ALTER COLUMN completion_tokens TYPE INTEGER USING completion_tokens::integer;
ALTER TABLE model_call_log ALTER COLUMN total_tokens TYPE INTEGER USING total_tokens::integer;
ALTER TABLE model_call_log ALTER COLUMN estimated_cost TYPE DOUBLE PRECISION USING estimated_cost::double precision;

CREATE INDEX IF NOT EXISTS idx_model_call_log_provider_code ON model_call_log (provider_code);
CREATE INDEX IF NOT EXISTS idx_model_call_log_model_code ON model_call_log (model_code);
