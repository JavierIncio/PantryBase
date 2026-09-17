package com.pantrybase.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;


@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PantryApiApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GenericContainer<?> redisContainer;

    @Test
	void contextLoads() throws IOException, InterruptedException {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history;", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(1);

        String pong = redisContainer
                .execInContainer("redis-cli", "PING")
                .getStdout().trim();
        assertThat(pong).isEqualTo("PONG");
	}

}
