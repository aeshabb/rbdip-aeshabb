package com.rbdip.bookstore.purchase;

public interface PurchaseHistory {

    boolean hasPurchased(Long productId, String customerName);
}
