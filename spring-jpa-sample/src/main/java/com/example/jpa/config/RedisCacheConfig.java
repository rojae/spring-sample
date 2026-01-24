package com.example.jpa.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정
 *
 * 특징:
 * - 별도 프로세스로 동작하는 인메모리 데이터 스토어
 * - 다양한 데이터 구조 지원 (String, Hash, List, Set, Sorted Set)
 * - 영속성 옵션 (RDB, AOF)
 * - Pub/Sub, Lua 스크립팅 지원
 *
 * 장점:
 * - 애플리케이션과 독립적인 생명주기
 * - 서버 재시작해도 캐시 유지 가능
 * - 언어/플랫폼 독립적
 * - Sentinel, Cluster로 고가용성 구성
 *
 * 단점:
 * - 네트워크 홉 발생 (로컬 캐시보다 느림)
 * - 별도 인프라 관리 필요
 * - 직렬화/역직렬화 오버헤드
 *
 * 실무 패턴:
 * - Look-Aside: 캐시 미스 시 DB 조회 후 캐시 저장
 * - Write-Through: 쓰기 시 캐시와 DB 동시 업데이트
 * - Write-Behind: 쓰기 시 캐시만 업데이트, 비동기로 DB 반영
 */
@Configuration
@Profile("redis")
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Lettuce: 비동기, Netty 기반 (권장)
        // Jedis: 동기, 멀티스레드 환경에서 커넥션 풀 필요
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 String으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();  // null 값 캐싱 안함

        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // members: 30분 TTL
        cacheConfigurations.put("members",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // products: 15분 TTL
        cacheConfigurations.put("products",
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // teams: 1시간 TTL (거의 안 바뀜)
        cacheConfigurations.put("teams",
                defaultConfig.entryTtl(Duration.ofHours(1)));

        // membersByStatus: 5분 TTL (자주 변경)
        cacheConfigurations.put("membersByStatus",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()  // 트랜잭션 연동
                .build();
    }
}
