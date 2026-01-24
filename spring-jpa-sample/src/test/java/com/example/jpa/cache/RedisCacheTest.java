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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 캐시 테스트
 *
 * 테스트 전 docker-compose 실행 필요:
 * cd spring-jpa-sample && docker-compose up -d
 *
 * 테스트 실행:
 * ./gradlew :spring-jpa-sample:test --tests "*RedisCacheTest"
 */
@SpringBootTest
@ActiveProfiles("redis")
class RedisCacheTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Redis 캐시 초기화
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 테스트 데이터 생성
        Member member = Member.builder()
                .name("Redis테스트")
                .email("redis-" + System.currentTimeMillis() + "@example.com")
                .status(MemberStatus.ACTIVE)
                .build();
        memberRepository.save(member);
        memberId = member.getId();
    }

    @Test
    @DisplayName("Redis: 연결 확인")
    void redis_connectionTest() {
        // given & when
        String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();

        // then
        assertThat(pong).isEqualTo("PONG");
        System.out.println("Redis 연결 성공: " + pong);
    }

    @Test
    @DisplayName("Redis: String 타입 저장/조회")
    void redis_stringOperations() {
        // given
        var ops = redisTemplate.opsForValue();

        // when
        ops.set("test:key1", "value1");
        ops.set("test:key2", "value2", Duration.ofSeconds(60));

        // then
        assertThat(ops.get("test:key1")).isEqualTo("value1");
        assertThat(ops.get("test:key2")).isEqualTo("value2");

        Long ttl = redisTemplate.getExpire("test:key2", TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);
        System.out.println("TTL: " + ttl + "초");

        // cleanup
        redisTemplate.delete("test:key1");
        redisTemplate.delete("test:key2");
    }

    @Test
    @DisplayName("Redis: Hash 타입 저장/조회")
    void redis_hashOperations() {
        // given
        var ops = redisTemplate.opsForHash();
        String hashKey = "test:member:1";

        // when
        ops.put(hashKey, "name", "홍길동");
        ops.put(hashKey, "email", "hong@example.com");
        ops.put(hashKey, "status", "ACTIVE");

        // then
        assertThat(ops.get(hashKey, "name")).isEqualTo("홍길동");
        assertThat(ops.get(hashKey, "email")).isEqualTo("hong@example.com");
        assertThat(ops.size(hashKey)).isEqualTo(3);

        var entries = ops.entries(hashKey);
        System.out.println("Hash entries: " + entries);

        // cleanup
        redisTemplate.delete(hashKey);
    }

    @Test
    @DisplayName("Redis: List 타입 저장/조회")
    void redis_listOperations() {
        // given
        var ops = redisTemplate.opsForList();
        String listKey = "test:list";

        // when
        ops.rightPush(listKey, "item1");
        ops.rightPush(listKey, "item2");
        ops.rightPush(listKey, "item3");

        // then
        assertThat(ops.size(listKey)).isEqualTo(3);
        assertThat(ops.leftPop(listKey)).isEqualTo("item1");
        assertThat(ops.range(listKey, 0, -1)).containsExactly("item2", "item3");

        // cleanup
        redisTemplate.delete(listKey);
    }

    @Test
    @DisplayName("Redis: Spring Cache 통합")
    void redis_springCacheIntegration() {
        // given
        var cache = cacheManager.getCache("members");
        assertThat(cache).isNotNull();

        // 직접 캐시에 저장
        cache.put("test-key", "test-value");

        // then
        assertThat(cache.get("test-key")).isNotNull();
        assertThat(cache.get("test-key").get()).isEqualTo("test-value");

        // Redis 키 확인
        Set<String> keys = redisTemplate.keys("members*");
        System.out.println("=== Redis 캐시 키 ===");
        if (keys != null) {
            keys.forEach(System.out::println);
        }

        // cleanup
        cache.evict("test-key");
    }

    @Test
    @DisplayName("Redis: TTL (Time To Live) 테스트")
    void redis_ttl() throws InterruptedException {
        // given
        var ops = redisTemplate.opsForValue();
        String key = "test:ttl";

        // when - 2초 TTL로 저장
        ops.set(key, "will expire", Duration.ofSeconds(2));

        // then - 즉시 조회 가능
        assertThat(ops.get(key)).isEqualTo("will expire");

        // 3초 후 만료
        Thread.sleep(3000);
        assertThat(ops.get(key)).isNull();
    }

    @Test
    @DisplayName("Redis: 캐시 제거 (evict)")
    void redis_evict() {
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
    @DisplayName("Redis: 전체 캐시 제거")
    void redis_clearAll() {
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
    @DisplayName("Redis: 키 패턴 조회")
    void redis_keyPattern() {
        // given
        var ops = redisTemplate.opsForValue();
        ops.set("user:1:name", "홍길동");
        ops.set("user:1:email", "hong@example.com");
        ops.set("user:2:name", "김철수");
        ops.set("product:1:name", "상품1");

        // when
        Set<String> userKeys = redisTemplate.keys("user:*");
        Set<String> productKeys = redisTemplate.keys("product:*");

        // then
        System.out.println("User keys: " + userKeys);
        System.out.println("Product keys: " + productKeys);

        assertThat(userKeys).hasSize(3);
        assertThat(productKeys).hasSize(1);

        // cleanup
        redisTemplate.delete(userKeys);
        redisTemplate.delete(productKeys);
    }

    @Test
    @DisplayName("Redis: 원자적 증가 (Atomic Increment)")
    void redis_atomicIncrement() {
        // given
        var ops = redisTemplate.opsForValue();
        String counterKey = "test:counter";
        ops.set(counterKey, 0);  // Integer 0으로 설정 (JSON 직렬화되어도 숫자로 저장됨)

        // when
        Long count1 = ops.increment(counterKey);
        Long count2 = ops.increment(counterKey);
        Long count3 = ops.increment(counterKey, 5);

        // then
        assertThat(count1).isEqualTo(1);
        assertThat(count2).isEqualTo(2);
        assertThat(count3).isEqualTo(7);

        // cleanup
        redisTemplate.delete(counterKey);
    }

    @Test
    @DisplayName("Redis: 메모리 정보 확인")
    void redis_memoryInfo() {
        // given & when
        var connection = redisTemplate.getConnectionFactory().getConnection();
        var info = connection.info("memory");

        // then
        System.out.println("=== Redis 메모리 정보 ===");
        if (info != null) {
            info.forEach((key, value) ->
                    System.out.println(key + ": " + value));
        }
        assertThat(info).isNotNull();
    }

    @Test
    @DisplayName("Redis: 캐시 저장소별 키 확인")
    void redis_cacheStoreKeys() {
        // given
        var membersCache = cacheManager.getCache("members");
        var productsCache = cacheManager.getCache("products");

        if (membersCache != null) {
            membersCache.put("test-member", "member-value");
        }
        if (productsCache != null) {
            productsCache.put("test-product", "product-value");
        }

        // when
        Set<String> allKeys = redisTemplate.keys("*");

        // then
        System.out.println("=== Redis 전체 키 ===");
        if (allKeys != null) {
            allKeys.forEach(key -> {
                String type = redisTemplate.type(key).code();
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                System.out.println(String.format("Key: %s, Type: %s, TTL: %ds", key, type, ttl));
            });
        }
    }
}
