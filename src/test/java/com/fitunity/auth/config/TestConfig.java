package com.fitunity.auth.config;

import com.fitunity.auth.service.RedisService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to disable Redis dependencies during tests.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public RedisService redisService() {
        return mock(RedisService.class);
    }
}
