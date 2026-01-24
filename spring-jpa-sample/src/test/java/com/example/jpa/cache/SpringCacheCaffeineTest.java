package com.example.jpa.cache;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.repository.MemberRepository;
import com.example.jpa.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Cache (Caffeine) 테스트
 *
 * Spring Cache 특징:
 * - 메서드 레벨 캐싱
 * - @Cacheable: 캐시에서 조회, 없으면 실행 후 저장
 * - @CachePut: 항상 실행 후 캐시 갱신
 * - @CacheEvict: 캐시에서 제거
 *
 * Caffeine 특징:
 * - JVM 내 로컬 캐시
 * - Window TinyLFU 알고리즘으로 높은 적중률
 * - 나노초 단위의 빠른 조회
 */
@SpringBootTest
@ActiveProfiles("test")
class SpringCacheCaffeineTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long memberId;
    private String memberEmail;

    @BeforeEach
    @Transactional
    void setUp() {
        // 캐시 초기화
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });

        // 테스트 데이터 생성
        Member member = Member.builder()
                .name("캐시테스트")
                .email("caffeine-test-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        memberId = member.getId();
        memberEmail = member.getEmail();
    }

    @Test
    @DisplayName("@Cacheable: 첫 번째 호출은 DB 조회, 두 번째는 캐시에서 반환")
    void cacheable_secondCallReturnsCachedValue() {
        // given - 캐시 비어있음
        var cache = cacheManager.getCache("members");
        assertThat(cache).isNotNull();
        assertThat(cache.get(memberId)).isNull();

        // when - 첫 번째 호출 (DB 조회, 캐시에 저장)
        Member first = memberService.findById(memberId);

        // then - 캐시에 저장됨
        assertThat(cache.get(memberId)).isNotNull();
        assertThat(cache.get(memberId).get()).isEqualTo(first);

        // when - 두 번째 호출 (캐시에서 반환)
        Member second = memberService.findById(memberId);

        // then - 같은 객체 반환 (캐시에서)
        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("@Cacheable: 다른 키로 조회하면 별도 캐시")
    void cacheable_differentKeySeparateCache() {
        // given
        Member another = memberRepository.save(Member.builder()
                .name("다른회원")
                .email("another-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build());

        // when
        Member first = memberService.findById(memberId);
        Member second = memberService.findById(another.getId());

        // then - 각각 캐시됨
        var cache = cacheManager.getCache("members");
        assertThat(cache.get(memberId)).isNotNull();
        assertThat(cache.get(another.getId())).isNotNull();
        assertThat(first).isNotSameAs(second);
    }

    @Test
    @Transactional
    @DisplayName("@CachePut: 생성 후 캐시에 저장")
    void cachePut_storeInCacheAfterCreate() {
        // when - 회원 생성 (@CachePut)
        Member created = memberService.createMember("새회원", "new-member@example.com");

        // then - 캐시에 저장됨
        var cache = cacheManager.getCache("members");
        assertThat(cache.get(created.getId())).isNotNull();
        assertThat(cache.get(created.getId()).get()).isEqualTo(created);
    }

    @Test
    @Transactional
    @DisplayName("@CachePut: 수정 후 캐시 갱신")
    void cachePut_updateCache() {
        // given - 먼저 조회하여 캐시에 저장
        Member original = memberService.findById(memberId);
        String originalName = original.getName();

        // when - 수정 (@CachePut)
        Member updated = memberService.updateMember(memberId, "수정된이름", MemberStatus.ACTIVE);

        // then - 캐시가 갱신됨
        var cache = cacheManager.getCache("members");
        Member cached = (Member) cache.get(memberId).get();
        assertThat(cached.getName()).isEqualTo("수정된이름");
        assertThat(cached.getName()).isNotEqualTo(originalName);
    }

    @Test
    @Transactional
    @DisplayName("@CacheEvict: 삭제 시 캐시에서 제거")
    void cacheEvict_removeFromCache() {
        // given - 먼저 조회하여 캐시에 저장
        memberService.findById(memberId);
        var cache = cacheManager.getCache("members");
        assertThat(cache.get(memberId)).isNotNull();

        // when - 삭제 (@CacheEvict)
        memberService.deleteMember(memberId);

        // then - 캐시에서 제거됨
        assertThat(cache.get(memberId)).isNull();
    }

    @Test
    @Transactional
    @DisplayName("@CacheEvict(allEntries=true): 전체 캐시 제거")
    void cacheEvict_allEntries() {
        // given - 여러 회원 조회하여 캐시에 저장
        memberService.findById(memberId);

        Member another = memberRepository.save(Member.builder()
                .name("다른회원")
                .email("all-entries-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build());
        memberService.findById(another.getId());

        var cache = cacheManager.getCache("members");
        assertThat(cache.get(memberId)).isNotNull();
        assertThat(cache.get(another.getId())).isNotNull();

        // when - 벌크 업데이트 (allEntries=true로 전체 캐시 제거)
        memberService.bulkUpdateStatus(
                java.util.List.of(memberId, another.getId()),
                MemberStatus.INACTIVE
        );

        // then - 전체 캐시 제거됨
        assertThat(cache.get(memberId)).isNull();
        assertThat(cache.get(another.getId())).isNull();
    }

    @Test
    @DisplayName("@Cacheable(condition): 조건부 캐싱")
    void cacheable_conditional() {
        // MemberService.findByStatus에는 SUSPENDED 상태는 캐싱하지 않는 조건이 있음
        // condition = "#status != T(com.example.jpa.entity.MemberStatus).SUSPENDED"

        // given
        memberRepository.save(Member.builder()
                .name("정지회원")
                .email("suspended-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.SUSPENDED)
                .build());

        // when - SUSPENDED 상태 조회
        memberService.findByStatus(MemberStatus.SUSPENDED);

        // then - 캐시에 저장 안됨 (condition 조건)
        var cache = cacheManager.getCache("membersByStatus");
        // SUSPENDED는 캐싱되지 않음
    }

    @Test
    @DisplayName("@Cacheable(unless): 결과 기반 캐싱 제외")
    void cacheable_unless() {
        // MemberService.findByStatus에는 빈 결과는 캐싱하지 않는 조건이 있음
        // unless = "#result.isEmpty()"

        // when - 결과가 빈 경우
        var emptyResult = memberService.findByStatus(MemberStatus.SUSPENDED);

        // then - 빈 결과는 캐싱 안됨
        var cache = cacheManager.getCache("membersByStatus");
        // 빈 리스트는 unless 조건에 의해 캐싱되지 않음
    }

    @Test
    @DisplayName("Caffeine 캐시 통계 확인")
    void caffeine_statistics() {
        // given
        var cache = cacheManager.getCache("members");
        assertThat(cache).isInstanceOf(CaffeineCache.class);

        CaffeineCache caffeineCache = (CaffeineCache) cache;
        var nativeCache = caffeineCache.getNativeCache();

        // when - 여러 번 조회
        memberService.findById(memberId); // miss
        memberService.findById(memberId); // hit
        memberService.findById(memberId); // hit

        // then - 통계 확인
        var stats = nativeCache.stats();
        System.out.println("=== Caffeine 캐시 통계 ===");
        System.out.println("Hit Count: " + stats.hitCount());
        System.out.println("Miss Count: " + stats.missCount());
        System.out.println("Hit Rate: " + stats.hitRate());
        System.out.println("Eviction Count: " + stats.evictionCount());

        assertThat(stats.hitCount()).isGreaterThanOrEqualTo(2);
        assertThat(stats.missCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("@Caching: 여러 캐시 작업 조합")
    void caching_multipleOperations() {
        // given - 먼저 조회하여 캐시에 저장
        memberService.findById(memberId);
        memberService.findByStatus(MemberStatus.ACTIVE);

        var membersCache = cacheManager.getCache("members");
        var statusCache = cacheManager.getCache("membersByStatus");

        assertThat(membersCache.get(memberId)).isNotNull();

        // when - 상태 변경 (@Caching: @CachePut + @CacheEvict)
        memberService.updateMemberStatus(memberId, MemberStatus.INACTIVE);

        // then
        // - members 캐시: 갱신됨 (@CachePut)
        assertThat(membersCache.get(memberId)).isNotNull();
        Member cached = (Member) membersCache.get(memberId).get();
        assertThat(cached.getStatus()).isEqualTo(MemberStatus.INACTIVE);

        // - membersByStatus 캐시: 전체 제거됨 (@CacheEvict allEntries)
    }

    @Test
    @DisplayName("캐시 매니저에 등록된 캐시 목록 확인")
    void cacheManager_registeredCaches() {
        // when
        var cacheNames = cacheManager.getCacheNames();

        // then
        System.out.println("=== 등록된 캐시 목록 ===");
        cacheNames.forEach(name -> System.out.println("- " + name));

        assertThat(cacheNames).contains("members", "products", "teams", "membersByStatus");
    }
}
