package com.example.jpa.repository;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * MemberRepository - Spring Data JPA 다양한 쿼리 방식 예제
 *
 * 쿼리 메서드 종류:
 * 1. 메서드 이름 기반 쿼리 (Query Method)
 * 2. @Query JPQL
 * 3. @Query Native SQL
 * 4. Named Query
 * 5. QueryDSL (별도 구현)
 */
public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    // ==================== 1. 메서드 이름 기반 쿼리 ====================

    Optional<Member> findByEmail(String email);

    List<Member> findByStatus(MemberStatus status);

    List<Member> findByNameContaining(String name);

    List<Member> findByNameStartingWithOrderByCreatedAtDesc(String prefix);

    boolean existsByEmail(String email);

    long countByStatus(MemberStatus status);

    // ==================== 2. @Query JPQL ====================

    @Query("SELECT m FROM Member m WHERE m.status = :status ORDER BY m.createdAt DESC")
    List<Member> findActiveMembers(@Param("status") MemberStatus status);

    @Query("SELECT m FROM Member m JOIN FETCH m.team WHERE m.id = :id")
    Optional<Member> findByIdWithTeam(@Param("id") Long id);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.orders WHERE m.id = :id")
    Optional<Member> findByIdWithOrders(@Param("id") Long id);

    // N+1 문제 해결을 위한 Fetch Join
    @Query("SELECT DISTINCT m FROM Member m LEFT JOIN FETCH m.orders")
    List<Member> findAllWithOrders();

    // DTO Projection
    @Query("SELECT new com.example.jpa.repository.dto.MemberSummary(m.id, m.name, m.email) FROM Member m")
    List<com.example.jpa.repository.dto.MemberSummary> findMemberSummaries();

    // ==================== 3. Native Query ====================

    @Query(value = "SELECT * FROM members WHERE status = :status", nativeQuery = true)
    List<Member> findByStatusNative(@Param("status") String status);

    // ==================== 4. 페이징 ====================

    Page<Member> findByStatus(MemberStatus status, Pageable pageable);

    // Slice: count 쿼리 없이 다음 페이지 존재 여부만 확인 (더 효율적)
    Slice<Member> findSliceByStatus(MemberStatus status, Pageable pageable);

    // ==================== 5. 벌크 연산 ====================

    @Modifying(clearAutomatically = true)  // 영속성 컨텍스트 자동 클리어
    @Query("UPDATE Member m SET m.status = :status WHERE m.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") MemberStatus status);

    @Modifying
    @Query("DELETE FROM Member m WHERE m.status = :status")
    int deleteByStatusBulk(@Param("status") MemberStatus status);

    // ==================== 6. Lock ====================

    // 비관적 락 (Pessimistic Lock)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithPessimisticLock(@Param("id") Long id);

    // 낙관적 락 (Optimistic Lock) - @Version 필드 필요
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithOptimisticLock(@Param("id") Long id);

    // ==================== 7. Query Hints (캐시, 읽기 전용 등) ====================

    @QueryHints(value = {
            @QueryHint(name = "org.hibernate.readOnly", value = "true"),  // 읽기 전용 (스냅샷 생성 안함)
            @QueryHint(name = "org.hibernate.fetchSize", value = "50"),   // JDBC Fetch Size
            @QueryHint(name = "org.hibernate.cacheable", value = "true") // 쿼리 캐시 활성화
    })
    @Query("SELECT m FROM Member m WHERE m.status = :status")
    List<Member> findByStatusReadOnly(@Param("status") MemberStatus status);

    // ==================== 8. EntityGraph (N+1 해결) ====================

    @EntityGraph(attributePaths = {"team", "orders"})
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithGraph(@Param("id") Long id);

    @EntityGraph(attributePaths = {"team"})
    List<Member> findEntityGraphByStatus(MemberStatus status);
}
