package com.pantrybase.api;

import com.pantrybase.api.auth.repository.RefreshTokenRepository;
import com.pantrybase.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {
    public static final String REGISTER = "/api/auth/register";
    public static final String LOGIN = "/api/auth/login";
    public static final String REFRESH = "/api/auth/refresh";
    public static final String LOGOUT = "/api/auth/logout";
    public static final String ME = "/api/users/me";



    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        cleanRateLimitBuckets();
    }

    private void cleanDatabase() {
        userRepository.deleteAll();
        refreshTokenRepository.deleteAll();
    }

    private void cleanRateLimitBuckets() {
        Set<String> keys = redisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
