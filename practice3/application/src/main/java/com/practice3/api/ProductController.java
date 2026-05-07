package com.practice3.api;

import com.practice3.metrics.AppMetrics;
import com.practice3.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;
    private final AppMetrics metrics;

    public ProductController(ProductService service, AppMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable("id") long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> put(@PathVariable("id") long id, @Valid @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }
}

