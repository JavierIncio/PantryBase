package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the USDA FoodData Central catalog client.
 *
 * <p>Builds the FDC-specific {@link RestClient} here — with its own base URL and
 * timeouts — so the adapter never depends on Boot's client auto-configuration,
 * and exposes it to the application as the {@link FoodCatalogPort}.</p>
 */
@Configuration
@EnableConfigurationProperties(FdcProperties.class)
public class FdcClientConfig {

    @Bean
    FoodCatalogPort foodCatalogPort(FdcProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());

        RestClient client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();

        return new FdcFoodCatalogClient(client, props);
    }
}
