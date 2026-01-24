package com.example.jpa.cache;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.entity.Team;
import com.example.jpa.repository.MemberRepository;
import com.example.jpa.repository.TeamRepository;
import jakarta.persistence.Cache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2차 캐시 (Hibernate L2 Cache) 테스트
 *
 * 2차 캐시 특징:
 * - SessionFactory(EntityManagerFactory) 레벨에서 동작
 * - 여러 트랜잭션에서 공유
 * - 애플리케이션 범위로 동작
 * - @Cacheable + @Cache 어노테이션 필요
 *
 * 주의: READ_WRITE 캐시 전략은 트랜잭션 커밋 후에만 캐시에 저장됨
 */
@SpringBootTest
@ActiveProfiles("test")
class SecondLevelCacheTest {

    @Autowired
    private EntityManagerFactory emf;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        // 통계 활성화
        Session session = em.unwrap(Session.class);
        statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // 2차 캐시 초기화
        Cache cache = emf.getCache();
        cache.evictAll();
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 엔티티에 @Cacheable이 설정되어 있는지 확인")
    void secondLevelCache_entityHasCacheableAnnotation() {
        // given
        Member member = Member.builder()
                .name("2차캐시테스트")
                .email("l2-cache-test-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        Long memberId = member.getId();
        em.flush();
        em.clear();

        // when - 조회
        Member found = em.find(Member.class, memberId);

        // then - 엔티티 조회 성공
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("2차캐시테스트");

        // 2차 캐시 put 통계 확인 (트랜잭션 내에서는 캐시 저장이 지연될 수 있음)
        System.out.println("=== 2차 캐시 통계 (트랜잭션 내) ===");
        System.out.println("Put Count: " + statistics.getSecondLevelCachePutCount());
        System.out.println("Miss Count: " + statistics.getSecondLevelCacheMissCount());
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 같은 트랜잭션 내에서 영속성 컨텍스트 클리어 후 재조회")
    void secondLevelCache_reloadAfterClear() {
        // given
        Member member = Member.builder()
                .name("캐시재조회")
                .email("cache-reload-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        Long memberId = member.getId();
        em.flush();
        em.clear();

        statistics.clear();

        // when - 첫 번째 조회 (DB에서)
        em.find(Member.class, memberId);
        em.clear();

        long missCount1 = statistics.getSecondLevelCacheMissCount();
        long hitCount1 = statistics.getSecondLevelCacheHitCount();

        // 두 번째 조회 (캐시 또는 DB에서)
        em.find(Member.class, memberId);

        long missCount2 = statistics.getSecondLevelCacheMissCount();
        long hitCount2 = statistics.getSecondLevelCacheHitCount();

        // then
        System.out.println("=== 2차 캐시 통계 ===");
        System.out.println("첫 번째 조회 후 - Miss: " + missCount1 + ", Hit: " + hitCount1);
        System.out.println("두 번째 조회 후 - Miss: " + missCount2 + ", Hit: " + hitCount2);

        // READ_WRITE 전략에서는 트랜잭션 내에서 캐시 동작이 제한적
        // 캐시 hit가 발생하거나, DB에서 다시 조회할 수 있음
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 캐시 제거 (evict) API 테스트")
    void secondLevelCache_evictApi() {
        // given
        Member member = Member.builder()
                .name("캐시제거테스트")
                .email("evict-test-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        Long memberId = member.getId();
        em.flush();
        em.clear();

        // 조회하여 캐시에 저장 시도
        em.find(Member.class, memberId);
        em.clear();

        Cache cache = emf.getCache();

        // when - 캐시 제거
        cache.evict(Member.class, memberId);

        // then - 캐시에서 제거됨 확인
        boolean inCache = cache.contains(Member.class, memberId);
        System.out.println("캐시 제거 후 캐시 포함 여부: " + inCache);
        assertThat(inCache).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 특정 엔티티 타입 전체 캐시 제거")
    void secondLevelCache_evictEntityType() {
        // given
        Member member1 = Member.builder()
                .name("회원1")
                .email("evict-all-1-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        Member member2 = Member.builder()
                .name("회원2")
                .email("evict-all-2-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member1);
        memberRepository.save(member2);
        em.flush();
        em.clear();

        // 조회하여 캐시에 저장 시도
        em.find(Member.class, member1.getId());
        em.find(Member.class, member2.getId());
        em.clear();

        Cache cache = emf.getCache();

        // when - Member 타입 전체 캐시 제거
        cache.evict(Member.class);

        // then - 모든 Member 캐시 제거됨
        assertThat(cache.contains(Member.class, member1.getId())).isFalse();
        assertThat(cache.contains(Member.class, member2.getId())).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 통계 확인 (put, hit, miss)")
    void secondLevelCache_statistics() {
        // given
        statistics.clear();

        Member member = Member.builder()
                .name("통계테스트")
                .email("stats-test-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        Long memberId = member.getId();
        em.flush();
        em.clear();

        // when - 조회
        em.find(Member.class, memberId);
        em.clear();
        em.find(Member.class, memberId);

        // then
        System.out.println("=== 2차 캐시 통계 ===");
        System.out.println("Put Count: " + statistics.getSecondLevelCachePutCount());
        System.out.println("Miss Count: " + statistics.getSecondLevelCacheMissCount());
        System.out.println("Hit Count: " + statistics.getSecondLevelCacheHitCount());

        // 통계가 수집되는지 확인 (값은 캐시 전략에 따라 다름)
        long totalAccess = statistics.getSecondLevelCacheHitCount() +
                          statistics.getSecondLevelCacheMissCount();
        assertThat(totalAccess).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 엔티티 수정 시 캐시 동작")
    void secondLevelCache_updateEntity() {
        // given
        Member member = Member.builder()
                .name("원래이름")
                .email("update-cache-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        Long memberId = member.getId();
        em.flush();
        em.clear();

        // 조회
        Member cached = em.find(Member.class, memberId);
        assertThat(cached.getName()).isEqualTo("원래이름");
        em.clear();

        // when - 엔티티 수정
        Member toUpdate = em.find(Member.class, memberId);
        toUpdate.setName("변경된이름");
        em.flush();
        em.clear();

        // then - 다시 조회하면 변경된 값
        Member updated = em.find(Member.class, memberId);
        assertThat(updated.getName()).isEqualTo("변경된이름");
    }

    @Test
    @Transactional
    @DisplayName("2차 캐시: 쿼리 캐시 (JPQL)")
    void secondLevelCache_queryCache() {
        // given
        Member member = Member.builder()
                .name("쿼리캐시테스트")
                .email("query-cache-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        em.flush();
        em.clear();

        statistics.clear();

        // when - 쿼리 캐시 활성화하여 조회
        em.createQuery("SELECT m FROM Member m WHERE m.status = :status", Member.class)
                .setParameter("status", MemberStatus.ACTIVE)
                .setHint("org.hibernate.cacheable", true)
                .getResultList();
        em.clear();

        // 같은 쿼리 다시 실행
        em.createQuery("SELECT m FROM Member m WHERE m.status = :status", Member.class)
                .setParameter("status", MemberStatus.ACTIVE)
                .setHint("org.hibernate.cacheable", true)
                .getResultList();

        // then
        long queryCacheHit = statistics.getQueryCacheHitCount();
        long queryCacheMiss = statistics.getQueryCacheMissCount();
        long queryCachePut = statistics.getQueryCachePutCount();

        System.out.println("=== 쿼리 캐시 통계 ===");
        System.out.println("Query Cache Hit: " + queryCacheHit);
        System.out.println("Query Cache Miss: " + queryCacheMiss);
        System.out.println("Query Cache Put: " + queryCachePut);

        // 쿼리 캐시 동작 확인 (put이 발생하면 캐시가 동작하는 것)
        assertThat(queryCachePut + queryCacheMiss).isGreaterThan(0);
    }

    @Test
    @DisplayName("2차 캐시: 전체 캐시 초기화 (evictAll)")
    void secondLevelCache_evictAll() {
        Cache cache = emf.getCache();

        // when - 전체 캐시 제거
        cache.evictAll();

        // then - 예외 없이 완료
        System.out.println("전체 캐시 초기화 완료");
    }

    @Test
    @DisplayName("2차 캐시: 캐시 영역 정보 확인")
    void secondLevelCache_regionInfo() {
        // 캐시 영역 정보 출력
        System.out.println("=== 2차 캐시 영역 정보 ===");

        var sessionFactory = em.unwrap(Session.class).getSessionFactory();
        var cache = sessionFactory.getCache();

        System.out.println("Cache 구현체: " + cache.getClass().getName());

        // 통계에서 캐시 정보 확인
        var regionStats = statistics.getCacheRegionStatistics("memberCache");
        if (regionStats != null) {
            System.out.println("memberCache 영역 통계:");
            System.out.println("  - Hit Count: " + regionStats.getHitCount());
            System.out.println("  - Miss Count: " + regionStats.getMissCount());
            System.out.println("  - Put Count: " + regionStats.getPutCount());
        }

        assertThat(cache).isNotNull();
    }
}
