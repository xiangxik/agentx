CREATE TABLE IF NOT EXISTS deployment_access_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    conversation_id BIGINT,
    entry_type VARCHAR(64) NOT NULL,
    domain_name VARCHAR(255),
    ip_address VARCHAR(128),
    user_agent VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deployment_access_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id),
    CONSTRAINT fk_deployment_access_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE INDEX IF NOT EXISTS idx_deployment_access_chatbot_created_at
    ON deployment_access_log (chatbot_id, created_at DESC, id DESC);