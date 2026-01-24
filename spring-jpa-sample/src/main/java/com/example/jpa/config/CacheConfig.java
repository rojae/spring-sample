package com.example.jpa.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

/**
 * JVM 레벨 캐시 설정 (Caffeine) - 기본 로컬 캐시
 *
 * Spring Cache Abstraction을 사용하여 메서드 레벨 캐싱
 * - @Cacheable: 캐시에서 조회, 없으면 실행 후 저장
 * - @CachePut: 항상 실행 후 캐시 갱신
 * - @CacheEvict: 캐시에서 제거
 *
 * Caffeine 특징:
 * - JVM 내 로컬 캐시 (단일 인스턴스에 적합)
 * - Google Guava Cache의 후속작
 * - Window TinyLFU 알고리즘으로 높은 적중률
 * - 나노초 단위의 빠른 조회
 *
 * 적합한 경우:
 * - 단일 서버 환경
 * - 캐시 불일치가 큰 문제가 아닌 데이터 (코드 테이블, 설정값 등)
 * - 최고의 조회 성능이 필요한 경우
 *
 * Profile: default, local, ehcache
 */
@Configuration
@EnableCaching
@Profile({"default", "local", "ehcache", "test"})
public class CacheConfig {

    /**
     * Caffeine 기반 Spring Cache Manager
     * - JVM 레벨 캐싱 (애플리케이션 레벨)
     * - 메서드 결과 캐싱에 사용
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 기본 캐시 설정
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)           // 초기 용량
                .maximumSize(500)               // 최대 500개 엔트리
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 10분 후 만료
                .expireAfterAccess(5, TimeUnit.MINUTES)  // 5분간 미접근 시 만료
                .recordStats());                // 통계 기록

        // 등록할 캐시 이름들
        cacheManager.setCacheNames(java.util.List.of(
                "members",
                "products",
                "teams",
                "membersByStatus"
        ));

        return cacheManager;
    }

    /**
     * 별도의 설정이 필요한 캐시용 빌더
     */
    @Bean
    public Caffeine<Object, Object> shortLivedCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.MINUTES);
    }

    @Bean
    public Caffeine<Object, Object> longLivedCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS);
    }
}
