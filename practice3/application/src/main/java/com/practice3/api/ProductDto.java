package com.practice3.api;

public record ProductDto(
        long id,
        String name,
        long priceCents,
        int stock,
        long version
) {}

