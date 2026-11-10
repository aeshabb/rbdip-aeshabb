package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricingCalculator {

    private static final int BULK_QUANTITY = 10;
    private static final BigDecimal BULK_FACTOR = new BigDecimal("0.95");
    private static final BigDecimal VIP_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal WHOLESALE_FACTOR = new BigDecimal("0.85");
    private static final BigDecimal COUPON_FACTOR = new BigDecimal("0.80");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_FACTOR = new BigDecimal("0.98");

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineItem item : items) {
            BigDecimal linePrice = item.price().multiply(BigDecimal.valueOf(item.quantity()));
            if (item.quantity() > BULK_QUANTITY) {
                linePrice = linePrice.multiply(BULK_FACTOR);
            }
            total = total.add(linePrice);
        }
        total = applyCustomerDiscount(total, customerType);
        total = applyCoupon(total, couponCode);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_FACTOR);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        if ("vip".equals(customerType)) {
            return total.multiply(VIP_FACTOR);
        }
        if ("wholesale".equals(customerType)) {
            return total.multiply(WHOLESALE_FACTOR);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if ("SAVE10".equals(couponCode)) {
            return total.subtract(BigDecimal.TEN);
        }
        if ("SAVE20PERCENT".equals(couponCode)) {
            return total.multiply(COUPON_FACTOR);
        }
        return total;
    }
}
