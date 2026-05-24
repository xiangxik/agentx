CREATE TABLE IF NOT EXISTS tenant (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE IF NOT EXISTS role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE TABLE IF NOT EXISTS plan (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    limits_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_quota (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    plan_id BIGINT NOT NULL,
    overrides_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_quota_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tenant_quota_plan FOREIGN KEY (plan_id) REFERENCES plan (id)
);

CREATE TABLE IF NOT EXISTS usage_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    quantity BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    actor_user_id BIGINT,
    action_type VARCHAR(128) NOT NULL,
    target_type VARCHAR(128) NOT NULL,
    target_id VARCHAR(128),
    result VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    context_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chatbot (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    language VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    public_code VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chatbot_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE IF NOT EXISTS chatbot_appearance (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL UNIQUE,
    theme_color VARCHAR(32) NOT NULL,
    welcome_message TEXT NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appearance_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id)
);

CREATE TABLE IF NOT EXISTS chatbot_behavior (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL UNIQUE,
    fallback_message TEXT NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_behavior_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id)
);

CREATE TABLE IF NOT EXISTS faq (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    language VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    alternate_questions TEXT,
    answer TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_faq_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id)
);

CREATE TABLE IF NOT EXISTS knowledge_source (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    source_uri TEXT,
    metadata_json TEXT NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_knowledge_source_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id)
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    knowledge_source_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    source_link TEXT,
    embedding_ref TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunk_source FOREIGN KEY (knowledge_source_id) REFERENCES knowledge_source (id)
);

CREATE TABLE IF NOT EXISTS conversation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT NOT NULL,
    anonymous_visitor_id VARCHAR(128) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_chatbot FOREIGN KEY (chatbot_id) REFERENCES chatbot (id)
);

CREATE TABLE IF NOT EXISTS message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE TABLE IF NOT EXISTS handoff_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(64),
    issue_summary TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_handoff_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE TABLE IF NOT EXISTS model_provider (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    base_url TEXT NOT NULL,
    encrypted_api_key TEXT,
    status VARCHAR(32) NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_call_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    chatbot_id BIGINT,
    conversation_id BIGINT,
    provider_id BIGINT,
    usage_type VARCHAR(32) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(18, 6) NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_call_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT fk_model_call_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
);

CREATE TABLE IF NOT EXISTS agent_session (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    handoff_record_id BIGINT NOT NULL,
    agent_user_id BIGINT,
    status VARCHAR(32) NOT NULL,
    assignment_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_session_handoff FOREIGN KEY (handoff_record_id) REFERENCES handoff_record (id)
);

CREATE TABLE IF NOT EXISTS ticket (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    agent_session_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    assignee_user_id BIGINT,
    summary TEXT NOT NULL,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_agent_session FOREIGN KEY (agent_session_id) REFERENCES agent_session (id)
);

CREATE TABLE IF NOT EXISTS async_job (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    locked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
