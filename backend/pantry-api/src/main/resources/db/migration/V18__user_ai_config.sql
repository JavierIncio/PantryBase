-- User AI configuration (H7-a). Encrypted API keys per provider.
-- Only one active provider per user; fallback uses system defaults.
CREATE TABLE user_ai_config (
    user_id BIGINT NOT NULL PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    active_provider VARCHAR(16) NOT NULL DEFAULT 'OLLAMA',
    -- Encrypted API key (Base64: IV(12) + ciphertext + authTag(16))
    encrypted_api_key TEXT,
    -- Provider-specific model selection
    ollama_model VARCHAR(64) NOT NULL DEFAULT 'llama3.1:8b',
    openrouter_model VARCHAR(64) NOT NULL DEFAULT 'meta-llama/llama-3.1-8b-instruct:free',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_ai_config_user ON user_ai_config(user_id);

COMMENT ON TABLE user_ai_config IS 'Per-user AI provider configuration with encrypted API keys';
COMMENT ON COLUMN user_ai_config.encrypted_api_key IS 'AES-GCM encrypted API key (Base64: IV + ciphertext + authTag)';