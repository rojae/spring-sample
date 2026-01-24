package com.example.jpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

/**
 * JPA 설정
 *
 * 1차 캐시 (영속성 컨텍스트):
 * - EntityManager 단위로 동작
 * - 트랜잭션 범위 내에서 같은 엔티티 조회 시 DB 쿼리 없이 캐시에서 반환
 * - 동일성(identity) 보장: em.find(id) == em.find(id)
 *
 * 2차 캐시 (SessionFactory/EntityManagerFactory 레벨):
 * - 애플리케이션 범위로 동작
 * - 여러 트랜잭션에서 공유
 * - EhCache, Infinispan 등 외부 캐시 프로바이더 필요
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.example.jpa.repository")
@EnableTransactionManagement
public class JpaConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
