package com.pantrybase.api.recipe.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Provides a {@link WebClient.Builder} bean for the recipe module. */
@Configuration
class RecipeWebClientConfig {

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}