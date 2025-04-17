package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
