package com.example.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Member Entity - 2차 캐시 적용 예제
 *
 * @Cache: Hibernate 2차 캐시 설정
 * - READ_ONLY: 읽기 전용 (수정 불가, 가장 빠름)
 * - READ_WRITE: 읽기/쓰기 (동시성 보장)
 * - NONSTRICT_READ_WRITE: 약한 일관성 (성능 우선)
 * - TRANSACTIONAL: JTA 트랜잭션 지원
 */
@Entity
@Table(name = "members")
@Cacheable  // JPA 표준 2차 캐시 활성화
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "memberCache")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 연관관계 - 지연 로딩 + 컬렉션 캐시
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 연관관계 편의 메서드
    public void addOrder(Order order) {
        orders.add(order);
        order.setMember(this);
    }

    public void changeTeam(Team team) {
        if (this.team != null) {
            this.team.getMembers().remove(this);
        }
        this.team = team;
        if (team != null) {
            team.getMembers().add(this);
        }
    }
}
