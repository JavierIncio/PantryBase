package com.pantrybase.api.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserAiConfigService {

    private final UserAiConfigRepository repository;
    private final AiEncryptionService encryption;

    public UserAiConfigService(UserAiConfigRepository repository, AiEncryptionService encryption) {
        this.repository = repository;
        this.encryption = encryption;
    }

    public Optional<UserAiConfig> getByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public UserAiConfig saveConfig(Long userId, AiProviderProperties.Provider.Type activeProvider,
                                   String apiKey, String ollamaModel, String openrouterModel) {
        UserAiConfig config = repository.findByUserId(userId)
                .orElseGet(() -> {
                    UserAiConfig newConfig = new UserAiConfig();
                    newConfig.setUserId(userId);
                    return newConfig;
                });

        config.setActiveProvider(activeProvider);
        config.setOllamaModel(ollamaModel != null ? ollamaModel : "llama3.1:8b");
        config.setOpenrouterModel(openrouterModel != null ? openrouterModel : "meta-llama/llama-3.1-8b-instruct:free");
        config.setEncryptedApiKey(encryption.encrypt(apiKey));

        return repository.save(config);
    }

    @Transactional
    public void deleteConfig(Long userId) {
        repository.deleteById(userId);
    }

    public String getDecryptedApiKey(UserAiConfig config) {
        return encryption.decrypt(config.getEncryptedApiKey());
    }
}