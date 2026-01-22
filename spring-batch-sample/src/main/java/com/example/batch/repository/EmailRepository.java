package com.example.batch.repository;

import com.example.batch.entity.Email;
import com.example.batch.entity.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {

    /**
     * 특정 상태의 이메일 조회
     */
    List<Email> findByStatusOrderByIdAsc(EmailStatus status);

    /**
     * PENDING 상태의 이메일 개수 조회
     */
    long countByStatus(EmailStatus status);

    /**
     * ID 범위로 이메일 조회 (파티셔닝용)
     */
    @Query("SELECT e FROM Email e WHERE e.status = :status AND e.id BETWEEN :minId AND :maxId ORDER BY e.id ASC")
    List<Email> findByStatusAndIdRange(
            @Param("status") EmailStatus status,
            @Param("minId") Long minId,
            @Param("maxId") Long maxId
    );

    /**
     * PENDING 상태의 최소/최대 ID 조회
     */
    @Query("SELECT MIN(e.id), MAX(e.id) FROM Email e WHERE e.status = :status")
    Object[] findMinMaxIdByStatus(@Param("status") EmailStatus status);

    /**
     * 특정 기간 동안의 발송 통계
     */
    @Query("""
        SELECT e.status, COUNT(e)
        FROM Email e
        WHERE e.updatedAt BETWEEN :startDate AND :endDate
        GROUP BY e.status
    """)
    List<Object[]> getStatistics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
