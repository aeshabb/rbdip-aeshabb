package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.purchase.PurchaseHistory;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class ReviewApiTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseHistory purchaseHistory;

    @Test
    void checksPurchasesForTheRequestedCustomerAndProduct() {
        Long book = createProduct("Book");
        Long other = createProduct("Other book");
        assertThat(purchaseHistory.hasPurchased(book, "Anna Petrovna Smith")).isFalse();
        var request = new CreateOrderRequest("Anna Petrovna Smith", "Address", null, null, null,
                List.of(new CreateOrderRequest.Item(book, 1)));
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(purchaseHistory.hasPurchased(book, "Anna Petrovna Smith")).isTrue();
        assertThat(purchaseHistory.hasPurchased(book, "Other Customer")).isFalse();
        assertThat(purchaseHistory.hasPurchased(other, "Anna Petrovna Smith")).isFalse();
    }

    @Test
    void keepsReviewsAvailableWithoutVerifiedPurchase() {
        Long book = createProduct("Book");
        Long other = createProduct("Other book");
        assertThat(restTemplate.postForEntity("/products/" + book + "/reviews", Map.of("rating", 5), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.postForEntity("/products/" + book + "/reviews",
                Map.of("rating", 4, "authorName", "Anna", "comment", "Good"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var response = restTemplate.getForEntity("/products/" + book + "/reviews", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyInAnyOrder(
                Map.of("authorName", "anonymous", "rating", 5, "comment", ""),
                Map.of("authorName", "Anna", "rating", 4, "comment", "Good"));
        assertThat(restTemplate.getForEntity("/products/" + other + "/reviews", List.class).getBody()).isEmpty();
    }

    private Long createProduct(String name) {
        var response = restTemplate.postForEntity("/products", Map.of("name", name, "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }
}
