package com.pantrybase.api.common.config;

import com.pantrybase.api.common.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Configuration class for rate limiting.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    /**
     * Bean for the Lua script used in the token bucket rate limiting algorithm.
     *
     * @return a DefaultRedisScript configured with the Lua script and result type
     */
    @Bean
    DefaultRedisScript<List> restrictTokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("common/ratelimit/restrict-token-bucket.lua"));
        script.setResultType(List.class); // {allowed, tokens} -> deserialized as List<Long>
        return script;
    }
}
