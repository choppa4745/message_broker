package com.practice3.service;

import com.practice3.api.ProductDto;
import com.practice3.api.UpdateProductRequest;

public interface ProductService {
    ProductDto getById(long id);
    ProductDto update(long id, UpdateProductRequest req);
    void resetData();
}

