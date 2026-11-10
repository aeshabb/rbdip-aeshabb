package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderValidator validator;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PricingCalculator pricingCalculator;
    private final OrderNotifier notifier;

    public OrderService(OrderValidator validator, OrderRepository orderRepository,
            OrderItemRepository orderItemRepository, PricingCalculator pricingCalculator, OrderNotifier notifier) {
        this.validator = validator;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pricingCalculator = pricingCalculator;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        List<OrderValidator.ValidatedItem> items = validator.validate(request);
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                items.stream().map(item -> new PricingCalculator.LineItem(
                        item.product().getPrice(), item.quantity())).toList(),
                request.customerType(), request.couponCode());
        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), "new"));
        for (OrderValidator.ValidatedItem item : items) {
            orderItemRepository.save(new OrderItem(
                    order.getId(), item.product().getName(), item.product().getPrice(), item.quantity()));
        }
        notifier.sendConfirmation(request.customerFullName(), order.getId(), total);
        return order;
    }
}
