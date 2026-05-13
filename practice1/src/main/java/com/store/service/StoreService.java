package com.store.service;

import com.store.dto.OrderItemRequest;
import com.store.entity.*;
import com.store.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;


    @Transactional
    public Order placeOrder(Long customerId, List<OrderItemRequest> itemRequests) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: id=" + customerId));

        Order order = Order.builder()
                .customer(customer)
                .orderDate(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .build();
        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest req : itemRequests) {
            Product product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product not found: id=" + req.getProductId()));

            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(req.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(req.getQuantity())
                    .subtotal(subtotal)
                    .build();
            orderItemRepository.save(item);

            total = total.add(subtotal);
        }

        order.setTotalAmount(total);
        order = orderRepository.save(order);

        log.info("Сценарий 1 — Заказ #{} размещён, сумма: {}", order.getOrderId(), total);
        return order;
    }


    @Transactional
    public Customer updateCustomerEmail(Long customerId, String newEmail) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: id=" + customerId));

        String oldEmail = customer.getEmail();
        customer.setEmail(newEmail);
        customer = customerRepository.save(customer);

        log.info("Сценарий 2 — E-mail клиента #{} изменён: {} -> {}",
                customerId, oldEmail, newEmail);
        return customer;
    }


    @Transactional
    public Product addProduct(String productName, BigDecimal price) {
        Product product = Product.builder()
                .productName(productName)
                .price(price)
                .build();
        product = productRepository.save(product);

        log.info("Сценарий 3 — Продукт добавлен: id={}, name='{}', price={}",
                product.getProductId(), productName, price);
        return product;
    }
}
