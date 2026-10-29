package io.paycore.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the full application against a real PostgreSQL. Uses Testcontainers by default; set
 * {@code TEST_DATABASE_URL} (plus optional {@code TEST_DATABASE_USERNAME}/{@code TEST_DATABASE_PASSWORD}) to point
 * at an existing database instead, e.g. where Docker is unavailable.
 *
 * <p>Tests share one database and never truncate it (the ledger is append-only). Each test creates its own users,
 * so assertions only look at data the test created, apart from global ledger invariants which must always hold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DATABASE_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> env("TEST_DATABASE_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> env("TEST_DATABASE_PASSWORD", "postgres"));
            return;
        }
        startContainer();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static synchronized void startContainer() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(rest);
    }
}
