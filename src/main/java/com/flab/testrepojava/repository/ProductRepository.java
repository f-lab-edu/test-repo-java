package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name); //완전히 일치하는 이름만 조회
    List<Product> findByNameContaining(String name); //Like '사과%'
}
