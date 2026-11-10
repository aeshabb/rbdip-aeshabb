package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class OrderListingTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @ParameterizedTest
    @ValueSource(ints = {1, 12})
    void fetchesAllCustomersAndItemsInOneQuery(int count) {
        Long first = createProduct("First");
        Long second = createProduct("Second");
        for (int i = 0; i < count; i++) {
            var request = new CreateOrderRequest("Customer " + i, "Address", null, null, null,
                    List.of(new CreateOrderRequest.Item(first, 1), new CreateOrderRequest.Item(second, 2)));
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(response.getBody()).hasSize(count);
        for (Object value : response.getBody()) {
            Map<?, ?> order = (Map<?, ?>) value;
            assertThat((String) order.get("customerFullName")).startsWith("Customer ");
            List<?> items = (List<?>) order.get("items");
            assertThat(items).hasSize(2);
            assertThat(items.stream().map(item -> (String) ((Map<?, ?>) item).get("productName")).toList())
                    .containsExactlyInAnyOrder("First", "Second");
        }
    }

    private Long createProduct(String name) {
        var response = restTemplate.postForEntity("/products", Map.of("name", name, "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }
}
