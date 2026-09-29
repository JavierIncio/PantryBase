package com.pantrybase.api.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class StubFoodCatalogPortConfig {

    @Bean
    @Primary
    StubFoodCatalogPort stubFoodCatalogPort() {
        return new StubFoodCatalogPort();
    }
}