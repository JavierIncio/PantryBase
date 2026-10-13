package com.pantrybase.api.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;

import java.time.Instant;

@Entity
@Table(name = "user_ai_config")
public class UserAiConfig {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private com.pantrybase.api.user.domain.User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_provider", nullable = false)
    private com.pantrybase.api.ai.AiProviderProperties.Provider.Type activeProvider = com.pantrybase.api.ai.AiProviderProperties.Provider.Type.OLLAMA;

    @Column(name = "encrypted_api_key", columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "ollama_model", nullable = false)
    private String ollamaModel = "llama3.1:8b";

    @Column(name = "openrouter_model", nullable = false)
    private String openrouterModel = "meta-llama/llama-3.1-8b-instruct:free";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters & Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public com.pantrybase.api.user.domain.User getUser() { return user; }
    public void setUser(com.pantrybase.api.user.domain.User user) { this.user = user; }

    public AiProviderProperties.Provider.Type getActiveProvider() { return activeProvider; }
    public void setActiveProvider(AiProviderProperties.Provider.Type activeProvider) { this.activeProvider = activeProvider; }

    public String getEncryptedApiKey() { return encryptedApiKey; }
    public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }

    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String ollamaModel) { this.ollamaModel = ollamaModel; }

    public String getOpenrouterModel() { return openrouterModel; }
    public void setOpenrouterModel(String openrouterModel) { this.openrouterModel = openrouterModel; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}