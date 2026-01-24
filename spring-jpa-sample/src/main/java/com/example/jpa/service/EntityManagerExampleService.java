package com.example.jpa.service;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.entity.Product;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * EntityManager 직접 사용 예제
 *
 * 영속성 컨텍스트 (Persistence Context):
 * - 엔티티를 영구 저장하는 환경
 * - 1차 캐시, 동일성 보장, 쓰기 지연, 변경 감지, 지연 로딩 제공
 *
 * 엔티티 생명주기:
 * 1. 비영속 (new/transient): JPA와 무관한 상태
 * 2. 영속 (managed): 영속성 컨텍스트에 저장된 상태
 * 3. 준영속 (detached): 영속성 컨텍스트에서 분리된 상태
 * 4. 삭제 (removed): 삭제된 상태
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityManagerExampleService {

    @PersistenceContext
    private EntityManager em;

    @PersistenceUnit
    private EntityManagerFactory emf;

    // ==================== 영속성 컨텍스트 기본 ====================

    /**
     * 1차 캐시 예제
     * - 같은 트랜잭션 내에서 동일한 엔티티 조회 시 캐시에서 반환
     */
    @Transactional(readOnly = true)
    public void firstLevelCacheExample(Long memberId) {
        log.info("=== 1차 캐시 예제 ===");

        // 첫 번째 조회: DB에서 가져옴
        Member member1 = em.find(Member.class, memberId);
        log.info("첫 번째 조회: {}", member1.getName());

        // 두 번째 조회: 1차 캐시에서 가져옴 (SQL 실행 안함)
        Member member2 = em.find(Member.class, memberId);
        log.info("두 번째 조회: {}", member2.getName());

        // 동일성 보장
        log.info("동일 객체 여부: {}", member1 == member2);  // true
    }

    /**
     * 쓰기 지연 (Write-Behind) 예제
     * - persist() 호출 시 SQL 즉시 실행 안함
     * - commit 시점에 모아서 실행
     */
    @Transactional
    public void writeBehindExample() {
        log.info("=== 쓰기 지연 예제 ===");

        Member member1 = Member.builder()
                .name("Member1")
                .email("member1@test.com")
                .status(MemberStatus.ACTIVE)
                .build();

        Member member2 = Member.builder()
                .name("Member2")
                .email("member2@test.com")
                .status(MemberStatus.ACTIVE)
                .build();

        log.info("persist 호출 전");
        em.persist(member1);  // 1차 캐시에 저장, SQL 실행 안함
        em.persist(member2);  // 1차 캐시에 저장, SQL 실행 안함
        log.info("persist 호출 후, 아직 SQL 실행 안됨");

        // flush 호출 시 SQL 실행 (또는 트랜잭션 커밋 시)
        em.flush();
        log.info("flush 후 SQL 실행됨");
    }

    /**
     * 변경 감지 (Dirty Checking) 예제
     * - 영속 상태 엔티티의 변경을 자동 감지
     * - 별도의 update() 호출 불필요
     */
    @Transactional
    public void dirtyCheckingExample(Long memberId) {
        log.info("=== 변경 감지 예제 ===");

        Member member = em.find(Member.class, memberId);
        log.info("원래 이름: {}", member.getName());

        // 엔티티 수정 (별도의 save/update 불필요)
        member.setName("변경된 이름");
        member.setStatus(MemberStatus.INACTIVE);

        // 커밋 시점에 자동으로 UPDATE SQL 실행
        log.info("트랜잭션 커밋 시 자동 UPDATE");
    }

    // ==================== 영속성 컨텍스트 관리 ====================

    /**
     * 준영속 상태 예제
     * - detach: 특정 엔티티를 준영속 상태로
     * - clear: 영속성 컨텍스트 초기화
     * - close: 영속성 컨텍스트 종료
     */
    @Transactional
    public void detachExample(Long memberId) {
        log.info("=== 준영속 상태 예제 ===");

        Member member = em.find(Member.class, memberId);
        log.info("영속 상태: {}", em.contains(member));  // true

        // 준영속 상태로 전환
        em.detach(member);
        log.info("준영속 상태: {}", em.contains(member));  // false

        // 준영속 상태에서 변경해도 DB 반영 안됨
        member.setName("이름 변경");  // 반영 안됨

        // 다시 영속 상태로 (merge)
        Member mergedMember = em.merge(member);
        log.info("merge 후 영속 상태: {}", em.contains(mergedMember));  // true
    }

    /**
     * refresh 예제
     * - DB에서 데이터를 다시 조회하여 엔티티 초기화
     */
    @Transactional
    public void refreshExample(Long memberId) {
        Member member = em.find(Member.class, memberId);
        member.setName("임시 변경");

        // DB의 데이터로 덮어쓰기
        em.refresh(member);
        log.info("refresh 후 이름: {}", member.getName());  // 원래 이름
    }

    // ==================== JPQL & Criteria ====================

    /**
     * JPQL 예제
     */
    @Transactional(readOnly = true)
    public List<Member> jpqlExample(MemberStatus status) {
        // 기본 JPQL
        String jpql = "SELECT m FROM Member m WHERE m.status = :status ORDER BY m.name";

        return em.createQuery(jpql, Member.class)
                .setParameter("status", status)
                .setFirstResult(0)  // offset
                .setMaxResults(10)  // limit
                .getResultList();
    }

    /**
     * Named Query 예제
     */
    @Transactional(readOnly = true)
    public List<Member> namedQueryExample(MemberStatus status) {
        return em.createNamedQuery("Member.findByStatus", Member.class)
                .setParameter("status", status)
                .getResultList();
    }

    // ==================== 캐시 관련 ====================

    /**
     * 2차 캐시 조회 예제
     */
    @Transactional(readOnly = true)
    public void secondLevelCacheExample(Long memberId) {
        log.info("=== 2차 캐시 예제 ===");

        // Hibernate Session 접근
        Session session = em.unwrap(Session.class);

        // 캐시 통계
        org.hibernate.stat.Statistics stats = session.getSessionFactory().getStatistics();
        stats.setStatisticsEnabled(true);

        Member member = em.find(Member.class, memberId);
        log.info("조회된 회원: {}", member.getName());

        // 캐시 적중률 확인
        log.info("2차 캐시 적중: {}", stats.getSecondLevelCacheHitCount());
        log.info("2차 캐시 미스: {}", stats.getSecondLevelCacheMissCount());
    }

    /**
     * 캐시 제어 예제
     */
    @Transactional
    public void cacheControlExample(Long memberId) {
        // 특정 엔티티 캐시 제거
        Cache cache = emf.getCache();

        if (cache.contains(Member.class, memberId)) {
            cache.evict(Member.class, memberId);
            log.info("Member {} 캐시 제거", memberId);
        }

        // 특정 엔티티 타입 전체 캐시 제거
        cache.evict(Member.class);

        // 모든 캐시 제거
        cache.evictAll();
    }

    /**
     * Query Hint를 통한 캐시 제어
     */
    @Transactional(readOnly = true)
    public List<Member> queryWithCacheHint(MemberStatus status) {
        return em.createQuery("SELECT m FROM Member m WHERE m.status = :status", Member.class)
                .setParameter("status", status)
                .setHint("org.hibernate.cacheable", true)  // 쿼리 캐시 활성화
                .setHint("org.hibernate.cacheRegion", "memberQueryCache")
                .getResultList();
    }

    // ==================== 고급 기능 ====================

    /**
     * NaturalId 조회 예제
     */
    @Transactional(readOnly = true)
    public Product findProductBySku(String sku) {
        Session session = em.unwrap(Session.class);

        // NaturalId 캐시 활용
        return session.byNaturalId(Product.class)
                .using("sku", sku)
                .load();
    }

    /**
     * 벌크 연산 예제
     * - 주의: 영속성 컨텍스트를 우회하므로 clear 필요
     */
    @Transactional
    public int bulkUpdateExample(MemberStatus fromStatus, MemberStatus toStatus) {
        int updatedCount = em.createQuery(
                        "UPDATE Member m SET m.status = :toStatus WHERE m.status = :fromStatus")
                .setParameter("fromStatus", fromStatus)
                .setParameter("toStatus", toStatus)
                .executeUpdate();

        // 영속성 컨텍스트 초기화 (벌크 연산 후 필수!)
        em.clear();

        return updatedCount;
    }

    /**
     * 저장 프로시저 호출 예제
     */
    @Transactional
    public void storedProcedureExample() {
        StoredProcedureQuery spq = em.createStoredProcedureQuery("update_member_status");
        spq.registerStoredProcedureParameter("memberId", Long.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter("newStatus", String.class, ParameterMode.IN);
        spq.registerStoredProcedureParameter("result", Integer.class, ParameterMode.OUT);

        spq.setParameter("memberId", 1L);
        spq.setParameter("newStatus", "ACTIVE");

        spq.execute();

        Integer result = (Integer) spq.getOutputParameterValue("result");
        log.info("프로시저 결과: {}", result);
    }

    /**
     * Lock 예제
     */
    @Transactional
    public Member findWithLock(Long memberId) {
        // 비관적 락
        return em.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 3000));  // 3초 타임아웃
    }
}
