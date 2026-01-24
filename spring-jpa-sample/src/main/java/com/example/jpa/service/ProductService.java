package com.example.jpa.service;

import com.example.jpa.entity.Product;
import com.example.jpa.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ProductService - 동시성 제어 및 락 예제
 *
 * 동시성 제어 전략:
 * 1. 낙관적 락 (Optimistic Lock): @Version 활용
 * 2. 비관적 락 (Pessimistic Lock): SELECT ... FOR UPDATE
 * 3. 트랜잭션 격리 수준 조절
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        log.info("Product 조회: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }

    @Cacheable(value = "products", key = "#sku")
    @Transactional(readOnly = true)
    public Product findBySku(String sku) {
        log.info("Product 조회 (SKU): {}", sku);
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }

    /**
     * 낙관적 락을 이용한 재고 감소
     * - @Version 필드가 변경되면 OptimisticLockException 발생
     * - 재시도 로직 필요
     */
    @Transactional
    @CachePut(value = "products", key = "#productId")
    public Product decreaseStockOptimistic(Long productId, int quantity) {
        log.info("낙관적 락으로 재고 감소: productId={}, quantity={}", productId, quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        product.decreaseStock(quantity);
        return product;
        // 커밋 시점에 버전 체크, 충돌 시 ObjectOptimisticLockingFailureException 발생
    }

    /**
     * 낙관적 락 + 재시도 로직
     */
    public Product decreaseStockWithRetry(Long productId, int quantity, int maxRetries) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                return decreaseStockOptimistic(productId, quantity);
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("낙관적 락 충돌, 재시도 {}/{}", retryCount, maxRetries);
                if (retryCount >= maxRetries) {
                    throw new RuntimeException("재고 감소 실패: 최대 재시도 횟수 초과", e);
                }
            }
        }
        throw new RuntimeException("재고 감소 실패");
    }

    /**
     * 비관적 락을 이용한 재고 감소
     * - SELECT ... FOR UPDATE
     * - 다른 트랜잭션이 대기
     */
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public Product decreaseStockPessimistic(Long productId, int quantity) {
        log.info("비관적 락으로 재고 감소: productId={}, quantity={}", productId, quantity);

        // FOR UPDATE 쿼리 실행
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        product.decreaseStock(quantity);
        return product;
    }

    /**
     * 격리 수준을 이용한 동시성 제어
     * - SERIALIZABLE: 가장 강력하지만 성능 저하
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = "products", key = "#productId")
    public Product decreaseStockSerializable(Long productId, int quantity) {
        log.info("SERIALIZABLE 격리 수준으로 재고 감소: productId={}, quantity={}", productId, quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        product.decreaseStock(quantity);
        return product;
    }

    @Transactional
    @CachePut(value = "products", key = "#result.id")
    public Product createProduct(String sku, String name, BigDecimal price, int stockQuantity) {
        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .price(price)
                .stockQuantity(stockQuantity)
                .build();

        return productRepository.save(product);
    }
}
