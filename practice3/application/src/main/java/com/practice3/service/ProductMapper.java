package com.practice3.service;

import com.practice3.api.ProductDto;
import com.practice3.model.Product;

public final class ProductMapper {
    private ProductMapper() {}

    public static ProductDto toDto(Product p) {
        return new ProductDto(p.id(), p.name(), p.priceCents(), p.stock(), p.version());
    }
}

