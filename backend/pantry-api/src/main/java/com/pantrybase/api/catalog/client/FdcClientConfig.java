package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FdcClientConfig.class);

    @Bean
    FoodCatalogPort foodCatalogPort(FdcProperties props) {
        if (props.isDemoKey()) {
            // Not fatal, because the public key does work, but it caps the catalog at
            // 30 requests per hour, which looks like a broken provider rather than a
            // missing configuration.
            log.warn("Using the public USDA FDC DEMO_KEY: the ingredient catalog is limited to "
                    + "30 requests/hour. Set USDA_FDC_API_KEY for a usable quota (~1000/hour).");
        }
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
