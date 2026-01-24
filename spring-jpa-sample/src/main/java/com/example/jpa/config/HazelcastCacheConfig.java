package com.example.jpa.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Hazelcast 분산 캐시 설정
 *
 * 특징:
 * - 여러 JVM 인스턴스가 클러스터를 구성해서 캐시 공유
 * - 임베디드 모드: 애플리케이션에 내장 (별도 서버 불필요)
 * - 클라이언트-서버 모드: 별도 Hazelcast 클러스터에 연결
 * - 자동 디스커버리: 멀티캐스트, TCP/IP, AWS, Kubernetes 등 지원
 *
 * 장점:
 * - 설정이 비교적 간단
 * - Near Cache로 로컬 캐시 + 분산 캐시 혼합 가능
 * - 네트워크 파티션 시 Split-Brain 보호
 *
 * vs Redis:
 * - 데이터가 JVM 메모리에 분산 저장 → 네트워크 지연 적음
 * - 애플리케이션과 생명주기 공유 (장단점)
 * - Java 객체 직렬화 최적화
 */
@Configuration
@EnableCaching
@Profile("hazelcast")
public class HazelcastCacheConfig {

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName("spring-jpa-hazelcast");

        // 네트워크 설정
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(true);

        // 클러스터 조인 설정 (개발환경: 멀티캐스트)
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(true);

        // 프로덕션에서는 TCP/IP 또는 클라우드 디스커버리 사용
        // joinConfig.getMulticastConfig().setEnabled(false);
        // joinConfig.getTcpIpConfig()
        //     .setEnabled(true)
        //     .addMember("192.168.1.100")
        //     .addMember("192.168.1.101");

        // Member 캐시 설정
        MapConfig memberCacheConfig = new MapConfig("members");
        memberCacheConfig.setTimeToLiveSeconds(600);  // 10분
        memberCacheConfig.setMaxIdleSeconds(300);     // 5분 미사용 시 제거
        memberCacheConfig.setEvictionConfig(
                new EvictionConfig()
                        .setMaxSizePolicy(MaxSizePolicy.PER_NODE)
                        .setSize(1000)
                        .setEvictionPolicy(EvictionPolicy.LRU)
        );
        // Near Cache: 로컬 캐시로 더 빠른 조회
        memberCacheConfig.setNearCacheConfig(
                new NearCacheConfig()
                        .setName("members-near")
                        .setTimeToLiveSeconds(60)
                        .setMaxIdleSeconds(30)
                        .setInvalidateOnChange(true)  // 원본 변경 시 무효화
        );
        config.addMapConfig(memberCacheConfig);

        // Product 캐시 설정
        MapConfig productCacheConfig = new MapConfig("products");
        productCacheConfig.setTimeToLiveSeconds(900);  // 15분
        productCacheConfig.setBackupCount(1);  // 복제본 1개
        productCacheConfig.setAsyncBackupCount(1);  // 비동기 복제본 1개
        config.addMapConfig(productCacheConfig);

        // Team 캐시 설정 (읽기 전용이므로 오래 유지)
        MapConfig teamCacheConfig = new MapConfig("teams");
        teamCacheConfig.setTimeToLiveSeconds(3600);  // 1시간
        config.addMapConfig(teamCacheConfig);

        // 기본 캐시 설정
        MapConfig defaultConfig = new MapConfig("default");
        defaultConfig.setTimeToLiveSeconds(300);
        config.addMapConfig(defaultConfig);

        return config;
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config config) {
        return Hazelcast.newHazelcastInstance(config);
    }

    @Bean
    public CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
        return new HazelcastCacheManager(hazelcastInstance);
    }
}
