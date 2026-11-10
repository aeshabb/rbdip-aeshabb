package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NormalizationMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void preservesExistingOrdersAndDeduplicatesContacts() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("1").load().migrate();
        jdbc.update("INSERT INTO products (name, price) VALUES ('Book', 10), ('Book', 10)");
        jdbc.update("""
                INSERT INTO orders (customer_full_name, customer_address, customer_phone)
                VALUES ('Ivan Petrov', 'Address', NULL), ('Ivan Petrov', 'Address', NULL),
                       ('Ivan Petrov', 'Other address', NULL), ('Anna', NULL, NULL)
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 10, 2), (2, 'Old book', 7, 1), (3, 'Old book', 7, 1)
                """);

        Flyway.configure().dataSource(source).target("2").load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT customer_id FROM orders WHERE id IN (1, 2)", Long.class))
                .containsOnly(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = 1", Long.class));
        assertThat(jdbc.queryForList("SELECT p.name FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id",
                String.class)).containsExactly("Book", "Old book", "Old book");
        assertThat(jdbc.queryForObject("SELECT sum(i.quantity * p.price) FROM order_items i JOIN products p ON p.id = i.product_id",
                BigDecimal.class)).isEqualByComparingTo("34.00");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE address IS NULL", String.class))
                .isEqualTo("Anna");
    }
}
