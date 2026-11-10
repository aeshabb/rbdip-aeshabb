package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class OrderValidationTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingNameOrAddress(String value) {
        Long productId = createProduct();
        List<CreateOrderRequest.Item> items = List.of(new CreateOrderRequest.Item(productId, 1));
        assertRejected(new CreateOrderRequest(value, "Address", null, null, null, items));
        assertRejected(new CreateOrderRequest("Customer", value, null, null, null, items));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidQuantity(int quantity) {
        assertRejected(request(List.of(new CreateOrderRequest.Item(createProduct(), quantity))));
    }

    @Test
    void rejectsMissingItemsAndUnknownProduct() {
        assertRejected(request(null));
        assertRejected(request(List.of(new CreateOrderRequest.Item(Long.MAX_VALUE, 1))));
    }

    @Test
    void defaultsQuantityAndReusesCustomer() {
        Long productId = createProduct();
        CreateOrderRequest request = request(List.of(new CreateOrderRequest.Item(productId, null)));
        for (int i = 0; i < 2; i++) {
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT quantity FROM order_items", Integer.class)).containsExactly(1, 1);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items", Long.class))
                .containsExactly(productId, productId);
    }

    private Long createProduct() {
        var response = restTemplate.postForEntity("/products", Map.of("name", "Book", "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private CreateOrderRequest request(List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest("Customer", "Address", null, null, null, items);
    }

    private void assertRejected(CreateOrderRequest request) {
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Integer.class)).isZero();
    }
}
