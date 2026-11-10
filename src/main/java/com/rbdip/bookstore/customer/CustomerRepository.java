package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query(value = """
            INSERT INTO customers (full_name, address, phone) VALUES (:fullName, :address, :phone)
            ON CONFLICT (full_name, address, phone) DO UPDATE SET full_name = EXCLUDED.full_name
            RETURNING id, full_name, address, phone
            """, nativeQuery = true)
    Customer getOrCreate(String fullName, String address, String phone);
}
