package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigrationApiLoadTest {

    private static final int CLIENTS = 3;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void keepsApiWorkingWhileContractRuns() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        migrate(source, "2");
        jdbc.update("INSERT INTO customers (full_name) VALUES ('Иван Иванов'), ('Prince'), ('Alice ')");
        migrate(source, "3");
        checkDualWrites(jdbc);
        migrate(source, "4");
        assertThat(jdbc.queryForList("SELECT first_name FROM customers ORDER BY id", String.class))
                .contains("Иван", "Prince", "Alice ", "Ann");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE first_name IS NULL OR last_name IS NULL",
                Integer.class)).isZero();

        try (var app = new SpringApplicationBuilder(BookstoreApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.enabled=false", "--logging.level.root=WARN")) {
            int port = ((WebServerApplicationContext) app).getWebServer().getPort();
            var rest = new TestRestTemplate(new RestTemplateBuilder().rootUri("http://localhost:" + port)
                    .setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)));
            Long productId = createProduct(rest);
            runLoadDuringContract(source, rest, productId);
            exerciseApi(rest, productId);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_name = 'customers' AND column_name = 'full_name'", Integer.class)).isZero();
    }

    private void checkDualWrites(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO customers (full_name) VALUES ('Иван Иванов')
                ON CONFLICT (full_name, address, phone) DO UPDATE SET full_name = EXCLUDED.full_name
                """);
        assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE full_name = 'Иван Иванов'", String.class))
                .isEqualTo("Иван");
        jdbc.update("INSERT INTO customers (full_name) VALUES ('Anna Smith')");
        assertThat(jdbc.queryForObject("SELECT last_name FROM customers WHERE full_name = 'Anna Smith'", String.class))
                .isEqualTo("Smith");
        jdbc.update("UPDATE customers SET full_name = 'Anna Jones' WHERE full_name = 'Anna Smith'");
        assertThat(jdbc.queryForObject("SELECT last_name FROM customers WHERE full_name = 'Anna Jones'", String.class))
                .isEqualTo("Jones");
        jdbc.update("UPDATE customers SET first_name = 'Ann' WHERE full_name = 'Anna Jones'");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE first_name = 'Ann'", String.class))
                .isEqualTo("Ann Jones");
        jdbc.update("INSERT INTO customers (first_name, last_name) VALUES ('Mary', 'Jane Watson')");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE first_name = 'Mary'", String.class))
                .isEqualTo("Mary Jane Watson");
    }

    private void runLoadDuringContract(DataSource source, TestRestTemplate rest, Long productId) throws Exception {
        var running = new AtomicBoolean(true);
        var completed = new AtomicInteger();
        var started = new CountDownLatch(CLIENTS);
        List<Future<?>> workers = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(CLIENTS)) {
            try {
                for (int i = 0; i < CLIENTS; i++) {
                    workers.add(pool.submit(() -> {
                        try {
                            exerciseApi(rest, productId);
                            started.countDown();
                            while (running.get()) {
                                exerciseApi(rest, productId);
                                completed.incrementAndGet();
                            }
                        } finally {
                            started.countDown();
                        }
                    }));
                }
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                migrate(source, "5");
            } finally {
                running.set(false);
            }
            for (Future<?> worker : workers) {
                worker.get(20, TimeUnit.SECONDS);
            }
        }
        assertThat(completed.get()).isPositive();
    }

    private void exerciseApi(TestRestTemplate rest, Long productId) {
        var request = new CreateOrderRequest("Load Customer", "Address", null, "regular", null,
                List.of(new CreateOrderRequest.Item(productId, 1)));
        var created = rest.postForEntity("/orders", request, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var listed = rest.getForEntity("/orders", List.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).isNotEmpty();
        for (Object value : listed.getBody()) {
            Map<?, ?> order = (Map<?, ?>) value;
            assertThat(order.get("customerFullName")).isEqualTo("Load Customer");
            assertThat((List<?>) order.get("items")).hasSize(1);
        }
    }

    private Long createProduct(TestRestTemplate rest) {
        var response = rest.postForEntity("/products", Map.of("name", "Book", "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void migrate(DataSource source, String target) {
        Flyway.configure().dataSource(source).target(target).load().migrate();
    }
}
