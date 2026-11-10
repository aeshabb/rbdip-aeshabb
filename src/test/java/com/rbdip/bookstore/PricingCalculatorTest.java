package com.rbdip.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.PricingCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
            "10, 1, regular, , 10.00",
            "10, 10, regular, , 100.00",
            "10, 11, regular, , 104.50",
            "100, 1, vip, , 90.00",
            "100, 1, wholesale, , 85.00",
            "100, 1, regular, SAVE10, 90.00",
            "100, 1, regular, SAVE20PERCENT, 80.00",
            "5, 1, regular, SAVE10, 0.00",
            "10, 1, regular, SAVE10, 0.00",
            "1000, 1, regular, , 1000.00",
            "1000.01, 1, regular, , 980.01",
            "-10, 1, regular, , 0.00",
            "10, -1, regular, , 0.00",
            "10, 0, regular, , 0.00",
            "1.005, 1, regular, , 1.01"
    })
    void preservesDiscountsAndBoundaries(String price, int quantity, String customerType,
            String coupon, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), customerType, coupon);
        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void emptyOrderCostsZero() {
        assertThat(calculator.calculateOrderTotal(List.of(), null, null)).isEqualTo(new BigDecimal("0.00"));
    }
}
