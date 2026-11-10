package com.rbdip.bookstore.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    default Customer getOrCreate(String fullName, String address, String phone) {
        int separator = fullName.indexOf(' ');
        if (separator < 0 || separator == fullName.length() - 1) {
            return getOrCreate(fullName, "", address, phone);
        }
        return getOrCreate(fullName.substring(0, separator), fullName.substring(separator + 1), address, phone);
    }

    @Query(value = """
            INSERT INTO customers (first_name, last_name, address, phone)
            VALUES (:firstName, :lastName, :address, :phone)
            ON CONFLICT (first_name, last_name, address, phone)
            DO UPDATE SET first_name = EXCLUDED.first_name
            RETURNING id, first_name, last_name, address, phone
            """, nativeQuery = true)
    Customer getOrCreate(String firstName, String lastName, String address, String phone);
}
