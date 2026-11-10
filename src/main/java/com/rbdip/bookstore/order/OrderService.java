package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    public OrderService(OrderValidator validator, OrderRepository orderRepository,
            OrderItemRepository orderItemRepository, PricingCalculator pricingCalculator, OrderNotifier notifier,
            CustomerRepository customerRepository) {
        this.validator = validator;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pricingCalculator = pricingCalculator;
        this.notifier = notifier;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        List<OrderValidator.ValidatedItem> items = validator.validate(request);
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                items.stream().map(item -> new PricingCalculator.LineItem(
                        item.product().getPrice(), item.quantity())).toList(),
                request.customerType(), request.couponCode());
        Customer customer = customerRepository.getOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, "new"));
        for (OrderValidator.ValidatedItem item : items) {
            orderItemRepository.save(new OrderItem(
                    order, item.product(), item.quantity()));
        }
        notifier.sendConfirmation(request.customerFullName(), order.getId(), total);
        return order;
    }
}
