package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            select count(i) > 0 from OrderItem i join i.order o join o.customer c
            where i.product.id = :productId
              and concat(c.firstName, case when c.lastName = '' then '' else concat(' ', c.lastName) end)
                  = :customerName
            """)
    boolean hasPurchased(Long productId, String customerName);
}
