package com.store;

import com.store.dto.OrderItemRequest;
import com.store.entity.Customer;
import com.store.entity.Order;
import com.store.entity.Product;
import com.store.repository.CustomerRepository;
import com.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemoRunner implements CommandLineRunner {

    private final StoreService storeService;
    private final CustomerRepository customerRepository;

    @Override
    public void run(String... args) {
        Customer customer = customerRepository.save(
                Customer.builder()
                        .firstName("Иван")
                        .lastName("Петров")
                        .email("ivan@example.com")
                        .build()
        );
        log.info("Создан клиент: {} {} ({})",
                customer.getFirstName(), customer.getLastName(), customer.getEmail());

        Product laptop = storeService.addProduct("Ноутбук", new BigDecimal("79999.99"));
        Product mouse  = storeService.addProduct("Мышь",     new BigDecimal("1499.50"));
        Product kbd    = storeService.addProduct("Клавиатура", new BigDecimal("3200.00"));

        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(laptop.getProductId(), 1),
                new OrderItemRequest(mouse.getProductId(),  2),
                new OrderItemRequest(kbd.getProductId(),    1)
        );
        Order order = storeService.placeOrder(customer.getCustomerId(), items);
        log.info("Заказ #{}: totalAmount = {}", order.getOrderId(), order.getTotalAmount());

        storeService.updateCustomerEmail(customer.getCustomerId(), "ivan.petrov@newmail.com");

        storeService.addProduct("Монитор 27\"", new BigDecimal("32499.00"));

    }
}
