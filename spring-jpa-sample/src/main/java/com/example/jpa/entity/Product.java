package com.example.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

import java.math.BigDecimal;

/**
 * Product Entity - NaturalId 캐시 예제
 *
 * @NaturalId: 비즈니스 키 (SKU 같은)를 기반으로 캐싱
 * @NaturalIdCache: NaturalId로 조회 시 캐시 활용
 */
@Entity
@Table(name = "products")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "productCache")
@NaturalIdCache  // NaturalId 캐시 활성화
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId  // 비즈니스 키 - 변경 불가
    @Column(nullable = false, unique = true, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Version  // 낙관적 락 (Optimistic Locking)
    private Long version;

    public void decreaseStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
    }
}
