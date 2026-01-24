package com.example.jpa.config;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.spring.embedded.provider.SpringEmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Infinispan 분산 캐시 설정
 *
 * 특징:
 * - Red Hat이 개발한 분산 인메모리 데이터 그리드
 * - JBoss/WildFly와 완벽 호환
 * - 임베디드 모드 / 클라이언트-서버 모드 지원
 * - Hibernate 2차 캐시 프로바이더로 많이 사용
 *
 * 캐시 모드:
 * - LOCAL: 로컬 캐시 (분산 안함) - 테스트용
 * - REPL_SYNC/ASYNC: 모든 노드에 복제 - 프로덕션용
 * - DIST_SYNC/ASYNC: 일부 노드에만 저장 (numOwners 설정)
 * - INVALIDATION: 변경 시 다른 노드의 캐시 무효화
 *
 * 설정 파일:
 * - 프로덕션: infinispan.xml (replicated-cache, 클러스터링)
 * - 테스트: infinispan-test.xml (local-cache, 단일 노드)
 */
@Configuration
@EnableCaching
@Profile("infinispan")
public class InfinispanCacheConfig {

    @Value("${infinispan.config.location:infinispan-test.xml}")
    private String configLocation;

    @Bean
    public EmbeddedCacheManager embeddedCacheManager() throws IOException {
        ClassPathResource resource = new ClassPathResource(configLocation);

        try (InputStream is = resource.getInputStream()) {
            return new DefaultCacheManager(is);
        }
    }

    @Bean
    public CacheManager cacheManager(EmbeddedCacheManager embeddedCacheManager) {
        return new SpringEmbeddedCacheManager(embeddedCacheManager);
    }
}
