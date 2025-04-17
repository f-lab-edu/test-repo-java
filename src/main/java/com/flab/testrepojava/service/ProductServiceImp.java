package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Product;
import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;

import java.util.Optional;

public interface ProductServiceImp {

    ProductResponse save(ProductRequest request);

    ProductResponse update(Long productId, ProductRequest updateParam);

    ProductResponse findById(Long id);

    void delete(Long id);
}
