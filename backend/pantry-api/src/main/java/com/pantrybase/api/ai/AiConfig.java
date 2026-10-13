package com.pantrybase.api.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiConfig {

    // Spring AI 2.0 auto-configura ChatClient.Builder con todos los modelos configurados
    // No necesitamos beans adicionales; FallbackChatClient recibe la lista de ChatClient inyectada
}