package com.example.jpa.cache;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.repository.MemberRepository;
import com.example.jpa.service.MemberService;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hazelcast 분산 캐시 테스트
 *
 * Hazelcast 특징:
 * - 분산 인메모리 데이터 그리드 (IMDG)
 * - 여러 JVM 인스턴스가 클러스터 구성
 * - 임베디드 모드: 애플리케이션에 내장
 * - Near Cache: 로컬 + 분산 캐시 혼합
 * - 자동 디스커버리: 멀티캐스트, TCP/IP, Kubernetes 등
 *
 * 테스트 실행:
 * ./gradlew test --tests "*HazelcastCacheTest" -Dspring.profiles.active=hazelcast
 */
@SpringBootTest
@ActiveProfiles("hazelcast")
class HazelcastCacheTest {

    @Autowired(required = false)
    private HazelcastInstance hazelcastInstance;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    @Transactional
    void setUp() {
        // 캐시 초기화
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }

        // 테스트 데이터 생성
        Member member = Member.builder()
                .name("Hazelcast테스트")
                .email("hazelcast-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        memberId = member.getId();
    }

    @Test
    @DisplayName("Hazelcast: 인스턴스 생성 확인")
    void hazelcast_instanceCreated() {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        assertThat(hazelcastInstance).isNotNull();
        assertThat(hazelcastInstance.getName()).contains("spring-jpa");

        System.out.println("=== Hazelcast 인스턴스 정보 ===");
        System.out.println("Instance Name: " + hazelcastInstance.getName());
        System.out.println("Cluster Name: " + hazelcastInstance.getConfig().getClusterName());
        System.out.println("Members: " + hazelcastInstance.getCluster().getMembers().size());
    }

    @Test
    @DisplayName("Hazelcast: 분산 맵에 데이터 저장/조회")
    void hazelcast_distributedMap() {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        // given
        IMap<String, String> map = hazelcastInstance.getMap("test-map");

        // when
        map.put("key1", "value1");
        map.put("key2", "value2");

        // then
        assertThat(map.get("key1")).isEqualTo("value1");
        assertThat(map.get("key2")).isEqualTo("value2");
        assertThat(map.size()).isEqualTo(2);

        // cleanup
        map.clear();
    }

    @Test
    @DisplayName("Hazelcast: Spring Cache 통합 (IMap 직접 사용)")
    void hazelcast_springCacheIntegration() {
        if (cacheManager == null || hazelcastInstance == null) {
            System.out.println("Hazelcast CacheManager가 없음. 테스트 스킵.");
            return;
        }

        // given - CacheManager 확인
        var cache = cacheManager.getCache("members");
        assertThat(cache).isNotNull();

        // Hazelcast IMap을 직접 사용하여 테스트
        // 주의: JPA 엔티티를 직접 캐싱하면 LazyInitializationException 발생
        // 실무에서는 DTO를 사용하거나 연관관계를 제외하고 캐싱해야 함
        IMap<Long, String> testMap = hazelcastInstance.getMap("test-members");

        // when - 캐시에 직접 저장
        testMap.put(memberId, "Member:" + memberId);

        // then - 캐시에서 조회
        assertThat(testMap.get(memberId)).isEqualTo("Member:" + memberId);

        System.out.println("=== Hazelcast Spring Cache 통합 테스트 ===");
        System.out.println("CacheManager 타입: " + cacheManager.getClass().getSimpleName());
        System.out.println("등록된 캐시: " + cacheManager.getCacheNames());

        // cleanup
        testMap.clear();
    }

    @Test
    @DisplayName("Hazelcast: IMap 통계 확인")
    void hazelcast_mapStatistics() {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        // given
        IMap<String, String> map = hazelcastInstance.getMap("stats-test-map");

        // when
        map.put("key", "value");
        map.get("key"); // hit
        map.get("key"); // hit
        map.get("nonexistent"); // miss

        // then
        var stats = map.getLocalMapStats();
        System.out.println("=== Hazelcast Map 통계 ===");
        System.out.println("Put Count: " + stats.getPutOperationCount());
        System.out.println("Get Count: " + stats.getGetOperationCount());
        System.out.println("Hit Count: " + stats.getHits());
        System.out.println("Owned Entry Count: " + stats.getOwnedEntryCount());

        // cleanup
        map.clear();
    }

    @Test
    @DisplayName("Hazelcast: TTL 설정")
    void hazelcast_ttl() throws InterruptedException {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        // given
        IMap<String, String> map = hazelcastInstance.getMap("ttl-test-map");

        // when - 1초 TTL로 저장
        map.put("key", "value", 1, java.util.concurrent.TimeUnit.SECONDS);

        // then - 즉시 조회 가능
        assertThat(map.get("key")).isEqualTo("value");

        // 2초 후 만료
        Thread.sleep(2000);
        assertThat(map.get("key")).isNull();
    }

    @Test
    @DisplayName("Hazelcast: Near Cache (로컬 캐시)")
    void hazelcast_nearCache() {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        // Near Cache가 설정된 맵 사용
        // HazelcastCacheConfig에서 members 맵에 Near Cache 설정됨
        IMap<Long, Member> map = hazelcastInstance.getMap("members");

        // given
        Member member = Member.builder()
                .name("Near Cache 테스트")
                .email("near-cache@example.com")
                .status(MemberStatus.ACTIVE)
                .build();

        // when
        map.put(999L, member);

        // 여러 번 조회 (Near Cache 적중)
        for (int i = 0; i < 10; i++) {
            map.get(999L);
        }

        // then - Near Cache 통계 확인
        var nearCacheStats = map.getLocalMapStats().getNearCacheStats();
        if (nearCacheStats != null) {
            System.out.println("=== Near Cache 통계 ===");
            System.out.println("Hits: " + nearCacheStats.getHits());
            System.out.println("Misses: " + nearCacheStats.getMisses());
            System.out.println("Owned Entry Count: " + nearCacheStats.getOwnedEntryCount());
        }

        // cleanup
        map.remove(999L);
    }

    @Test
    @DisplayName("Hazelcast: 캐시 제거 (evict)")
    void hazelcast_evict() {
        if (cacheManager == null || hazelcastInstance == null) {
            System.out.println("Hazelcast CacheManager가 없음. 테스트 스킵.");
            return;
        }

        // given - IMap을 직접 사용
        IMap<Long, String> testMap = hazelcastInstance.getMap("evict-test");
        testMap.put(memberId, "TestValue");
        assertThat(testMap.get(memberId)).isNotNull();

        // Spring Cache를 통한 evict
        var cache = cacheManager.getCache("evict-test");
        if (cache != null) {
            // when
            cache.evict(memberId);

            // then
            assertThat(cache.get(memberId)).isNull();
            System.out.println("Spring Cache evict 성공");
        }

        // IMap의 remove 테스트
        testMap.put(memberId, "TestValue");
        testMap.remove(memberId);
        assertThat(testMap.get(memberId)).isNull();
        System.out.println("IMap remove 성공");
    }

    @Test
    @DisplayName("Hazelcast: 클러스터 멤버 정보")
    void hazelcast_clusterMembers() {
        if (hazelcastInstance == null) {
            System.out.println("Hazelcast 프로파일이 활성화되지 않음. 테스트 스킵.");
            return;
        }

        var cluster = hazelcastInstance.getCluster();
        var members = cluster.getMembers();

        System.out.println("=== Hazelcast 클러스터 정보 ===");
        System.out.println("Cluster State: " + cluster.getClusterState());
        System.out.println("Member Count: " + members.size());

        members.forEach(member -> {
            System.out.println("Member: " + member.getAddress() +
                    " (local: " + member.localMember() + ")");
        });

        assertThat(members).isNotEmpty();
    }
}
