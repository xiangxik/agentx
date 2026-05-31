CREATE TABLE IF NOT EXISTS deployment_whitelist_domain (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deployment_domain_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id),
    CONSTRAINT uq_deployment_domain_chatbot UNIQUE (chatbot_id, domain)
);

CREATE INDEX IF NOT EXISTS idx_deployment_domain_chatbot ON deployment_whitelist_domain (chatbot_id);CREATE TABLE IF NOT EXISTS deployment_whitelist_domain (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deployment_domain_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id),
    CONSTRAINT uq_deployment_domain UNIQUE (chatbot_id, domain)
);

CREATE INDEX IF NOT EXISTS idx_deployment_domain_chatbot
    ON deployment_whitelist_domain (chatbot_id);