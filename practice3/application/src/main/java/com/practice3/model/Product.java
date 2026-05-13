package com.practice3.model;

public record Product(
        long id,
        String name,
        long priceCents,
        int stock,
        long version
) {}

