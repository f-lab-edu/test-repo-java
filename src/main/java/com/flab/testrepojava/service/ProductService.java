package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Product;
import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;
import com.flab.testrepojava.mapper.ProductMapper;
import com.flab.testrepojava.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductServiceImp {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public ProductResponse save(ProductRequest request) {
        Product product = productMapper.toEntity(request);
        Product saved = productRepository.save(product);
        return productMapper.toResponse(saved);
    }


    public List<ProductResponse> findAll() {
        List<Product> products = productRepository.findAll();
        return productMapper.toResponseList(products);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        productMapper.updateFromDto(request, product);
        productRepository.save(product);

        return productMapper.toResponse(product);

    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    //이름이 정확히 일치하는 상품 조회(캐시 없음)
    public ProductResponse findByName(String name) {
        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return productMapper.toResponse(product);
    }

    //이름에 일부가 포함된 상품 목록 조회(Redis 캐시 적용)
    @Cacheable(value = "productSearch", key = "#p0")
    public List<ProductResponse> searchByName(String name) {
        log.info(">> [CacheMiss] DB에서 검색 수행: {}", name);
        return productRepository.findByNameContaining(name).stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "productSearch", key = "p0")
    public void evictSearchCache(String name) {
        log.info(">> 캐시 삭제: {}", name);
    }
}