package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Product;
import com.flab.testrepojava.dto.ProductRequest;
import com.flab.testrepojava.dto.ProductResponse;
import com.flab.testrepojava.interceptor.RetryMetricsService;
import com.flab.testrepojava.mapper.ProductMapper;
import com.flab.testrepojava.repository.ProductRepository;
import com.flab.testrepojava.slack.SlackNotifier;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductServiceImp {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final SlackNotifier slackNotifier;
    private final RetryMetricsService retryMetricsService;

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

    @Retryable(
            value = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void decreaseQuantity(Long productId, int amount) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (product.getQuantity() < amount) {
            // 재고 부족은 재시도 의미가 없으므로 바로 알림 후 예외
            String message = String.format(
                    "❌ 재고 부족 - 상품 ID: %d, 요청 수량: %d, 현재 재고: %d",
                    productId, amount, product.getQuantity()
            );
            slackNotifier.send(message);
            throw new IllegalStateException("재고가 부족합니다.");
        }

        product.setQuantity(product.getQuantity() - amount);
    }

    // 낙관적 락 재시도 끝에 실패 시 호출
    @Recover
    public void recover(ObjectOptimisticLockingFailureException e, Long productId, int amount) {
        retryMetricsService.countRetry(e, productId);  // 재시도 횟수 카운트
        String message = String.format(
                "🔁 낙관적 락 재시도 실패 - 상품 ID: %d, 수량: %d, 에러: %s",
                productId, amount, e.getMessage()
        );
        slackNotifier.send(message);
    }

}