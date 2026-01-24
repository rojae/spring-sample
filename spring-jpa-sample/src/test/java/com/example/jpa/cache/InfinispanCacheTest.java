package com.example.jpa.cache;

import com.example.jpa.entity.Member;
import com.example.jpa.entity.MemberStatus;
import com.example.jpa.repository.MemberRepository;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infinispan 분산 캐시 테스트
 *
 * 테스트 전 docker-compose 실행 필요:
 * cd spring-jpa-sample && docker-compose up -d
 *
 * 테스트 실행:
 * ./gradlew :spring-jpa-sample:test --tests "*InfinispanCacheTest"
 */
@SpringBootTest
@ActiveProfiles("infinispan")
class InfinispanCacheTest {

    @Autowired
    private EmbeddedCacheManager embeddedCacheManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

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
                .name("Infinispan테스트")
                .email("infinispan-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        memberId = member.getId();
    }

    @Test
    @DisplayName("Infinispan: 캐시 매니저 생성 확인")
    void infinispan_cacheManagerCreated() {
        assertThat(embeddedCacheManager).isNotNull();
        assertThat(embeddedCacheManager.getStatus().toString()).isEqualTo("RUNNING");

        System.out.println("=== Infinispan 캐시 매니저 정보 ===");
        System.out.println("Name: " + embeddedCacheManager.getCacheManagerInfo().getName());
        System.out.println("Status: " + embeddedCacheManager.getStatus());
        System.out.println("Cache Names: " + embeddedCacheManager.getCacheNames());
    }

    @Test
    @DisplayName("Infinispan: 캐시 생성 및 데이터 저장/조회")
    void infinispan_cacheOperations() {
        // given
        var cache = embeddedCacheManager.getCache("test-cache");

        // when
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // then
        assertThat(cache.get("key1")).isEqualTo("value1");
        assertThat(cache.get("key2")).isEqualTo("value2");
        assertThat(cache.size()).isEqualTo(2);

        // cleanup
        cache.clear();
    }

    @Test
    @DisplayName("Infinispan: Spring Cache 통합")
    void infinispan_springCacheIntegration() {
        // given
        var cache = cacheManager.getCache("members");
        assertThat(cache).isNotNull();

        // 직접 캐시에 값 저장
        cache.put("test-key", "test-value");

        // then
        assertThat(cache.get("test-key")).isNotNull();
        assertThat(cache.get("test-key").get()).isEqualTo("test-value");

        // cleanup
        cache.evict("test-key");
    }

    @Test
    @DisplayName("Infinispan: TTL (Lifespan) 설정")
    void infinispan_lifespan() throws InterruptedException {
        // given
        var cache = embeddedCacheManager.getCache("ttl-test");

        // when - 2초 lifespan으로 저장
        cache.put("key", "value", 2, TimeUnit.SECONDS);

        // then - 즉시 조회 가능
        assertThat(cache.get("key")).isEqualTo("value");

        // 3초 후 만료
        Thread.sleep(3000);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    @DisplayName("Infinispan: Max Idle 설정")
    void infinispan_maxIdle() throws InterruptedException {
        // given
        var cache = embeddedCacheManager.getCache("idle-test");

        // when - lifespan 무제한, maxIdle 2초
        cache.put("key", "value", -1, TimeUnit.SECONDS, 2, TimeUnit.SECONDS);

        // then - 접근하면 idle 시간 리셋
        Thread.sleep(1000);
        assertThat(cache.get("key")).isEqualTo("value");

        Thread.sleep(1000);
        assertThat(cache.get("key")).isEqualTo("value");

        // 3초간 접근 안하면 만료
        Thread.sleep(3000);
        assertThat(cache.get("key")).isNull();
    }

    @Test
    @DisplayName("Infinispan: 캐시 통계 확인")
    @SuppressWarnings("deprecation")
    void infinispan_statistics() {
        // given
        var cache = embeddedCacheManager.getCache("stats-test");
        cache.clear();

        // when
        cache.put("key", "value");
        cache.get("key");
        cache.get("key");
        cache.get("nonexistent");

        // then
        var stats = cache.getAdvancedCache().getStats();
        System.out.println("=== Infinispan 캐시 통계 ===");
        System.out.println("Hits: " + stats.getHits());
        System.out.println("Misses: " + stats.getMisses());
        System.out.println("Stores: " + stats.getStores());

        assertThat(stats.getHits()).isGreaterThanOrEqualTo(2);
        assertThat(stats.getMisses()).isGreaterThanOrEqualTo(1);

        // cleanup
        cache.clear();
    }

    @Test
    @DisplayName("Infinispan: 캐시 제거 (evict)")
    void infinispan_evict() {
        // given
        var cache = cacheManager.getCache("members");
        cache.put("evict-test", "value");
        assertThat(cache.get("evict-test")).isNotNull();

        // when
        cache.evict("evict-test");

        // then
        assertThat(cache.get("evict-test")).isNull();
    }

    @Test
    @DisplayName("Infinispan: 전체 캐시 제거")
    void infinispan_clearAll() {
        // given
        var cache = cacheManager.getCache("members");
        cache.put("clear-test", "value");
        assertThat(cache.get("clear-test")).isNotNull();

        // when
        cache.clear();

        // then
        assertThat(cache.get("clear-test")).isNull();
    }

    @Test
    @DisplayName("Infinispan: 캐시 설정 정보 확인")
    void infinispan_cacheConfiguration() {
        System.out.println("=== 등록된 캐시 목록 ===");
        embeddedCacheManager.getCacheNames().forEach(name -> {
            var cache = embeddedCacheManager.getCache(name);
            var config = cache.getCacheConfiguration();

            System.out.println("\nCache: " + name);
            System.out.println("  Clustering Mode: " + config.clustering().cacheMode());
            System.out.println("  Lifespan: " + config.expiration().lifespan() + "ms");
            System.out.println("  Max Idle: " + config.expiration().maxIdle() + "ms");
            System.out.println("  Max Entries: " + config.memory().maxCount());
        });
    }

    @Test
    @DisplayName("Infinispan: 캐시 모드별 동작 확인 (LOCAL)")
    void infinispan_localCacheMode() {
        // LOCAL 모드 캐시
        var cache = embeddedCacheManager.getCache("local-query");
        var config = cache.getCacheConfiguration();

        System.out.println("=== LOCAL 캐시 모드 ===");
        System.out.println("Cache Mode: " + config.clustering().cacheMode());

        assertThat(config.clustering().cacheMode().isDistributed()).isFalse();
        assertThat(config.clustering().cacheMode().isReplicated()).isFalse();

        cache.put("local-key", "local-value");
        assertThat(cache.get("local-key")).isEqualTo("local-value");

        cache.clear();
    }

    @Test
    @DisplayName("Infinispan: 조건부 저장 (putIfAbsent)")
    void infinispan_putIfAbsent() {
        // given
        var cache = embeddedCacheManager.getCache("conditional-test");

        // when
        Object result1 = cache.putIfAbsent("key", "value1");
        Object result2 = cache.putIfAbsent("key", "value2");

        // then
        assertThat(result1).isNull();
        assertThat(result2).isEqualTo("value1");
        assertThat(cache.get("key")).isEqualTo("value1");

        cache.clear();
    }

    @Test
    @DisplayName("Infinispan: 조건부 교체 (replace)")
    void infinispan_replace() {
        // given
        var cache = embeddedCacheManager.getCache("replace-test");
        cache.put("key", "value1");

        // when
        boolean replaced1 = cache.replace("key", "value1", "value2");
        boolean replaced2 = cache.replace("key", "wrong", "value3");

        // then
        assertThat(replaced1).isTrue();
        assertThat(replaced2).isFalse();
        assertThat(cache.get("key")).isEqualTo("value2");

        cache.clear();
    }

    @Test
    @DisplayName("Infinispan: 클러스터 정보 확인 (단일 노드)")
    @SuppressWarnings("deprecation")
    void infinispan_clusterInfo() {
        var transport = embeddedCacheManager.getTransport();

        System.out.println("=== Infinispan 클러스터 정보 ===");
        System.out.println("Cache Manager Name: " + embeddedCacheManager.getCacheManagerInfo().getName());
        if (transport != null) {
            System.out.println("Node Address: " + transport.getAddress());
            System.out.println("Physical Address: " + transport.getPhysicalAddresses());
            System.out.println("Cluster Size: " + transport.getMembers().size());
            System.out.println("Members: " + transport.getMembers());
        } else {
            System.out.println("Transport is null (LOCAL mode only - no cluster)");
        }
    }
}
