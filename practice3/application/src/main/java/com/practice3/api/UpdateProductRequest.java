package com.practice3.api;

import jakarta.validation.constraints.Min;

public record UpdateProductRequest(
        String name,
        Long priceCents,
        @Min(0) Integer stock
) {}

