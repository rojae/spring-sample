# Spring JPA Sample

Spring Data JPA의 다양한 기술과 캐싱 전략을 소개하는 샘플 프로젝트입니다.

## 주요 기술

### 1. 캐싱 전략

#### 1차 캐시 (영속성 컨텍스트)
- EntityManager 단위로 동작
- 트랜잭션 범위 내에서 같은 엔티티 조회 시 DB 쿼리 없이 캐시에서 반환
- 동일성(identity) 보장

```java
@Transactional
public void example(Long id) {
    Member m1 = em.find(Member.class, id);  // DB 조회
    Member m2 = em.find(Member.class, id);  // 1차 캐시에서 반환
    assert m1 == m2;  // true
}
```

#### 2차 캐시 (Hibernate L2 Cache)
- EntityManagerFactory 레벨에서 동작
- 여러 트랜잭션에서 공유
- EhCache, Infinispan 등 외부 캐시 프로바이더 사용

```java
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "memberCache")
public class Member { ... }
```

**캐시 동시성 전략:**
| 전략 | 설명 |
|------|------|
| READ_ONLY | 읽기 전용, 가장 빠름 |
| READ_WRITE | 읽기/쓰기, 동시성 보장 |
| NONSTRICT_READ_WRITE | 약한 일관성, 성능 우선 |
| TRANSACTIONAL | JTA 트랜잭션 지원 |

#### JVM 캐시 (Spring Cache - Caffeine)
- 메서드 레벨 캐싱
- 애플리케이션 레벨에서 동작

```java
@Cacheable(value = "members", key = "#id")
public Member findById(Long id) { ... }

@CachePut(value = "members", key = "#result.id")
public Member update(Member member) { ... }

@CacheEvict(value = "members", key = "#id")
public void delete(Long id) { ... }
```

### 캐시 계층 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    애플리케이션 캐시                          │
│              (Spring Cache - @Cacheable)                    │
│         Caffeine, EhCache, Hazelcast, Redis 등              │
├─────────────────────────────────────────────────────────────┤
│                      2차 캐시 (L2)                           │
│           (Hibernate SessionFactory 레벨)                    │
│      EhCache, Hazelcast, Infinispan, Redisson 등            │
├─────────────────────────────────────────────────────────────┤
│                      1차 캐시 (L1)                           │
│              (영속성 컨텍스트, EntityManager)                  │
│                 트랜잭션 범위 내에서만 유효                     │
├─────────────────────────────────────────────────────────────┤
│                       Database                              │
└─────────────────────────────────────────────────────────────┘
```

---

### 1차 캐시 (L1 Cache) - 영속성 컨텍스트

| 항목 | 설명 |
|------|------|
| **범위** | EntityManager (트랜잭션) 단위 |
| **생명주기** | 트랜잭션 시작 ~ 종료 |
| **설정** | 자동 (별도 설정 불필요) |
| **공유** | 불가 (해당 트랜잭션에서만 유효) |

- JPA가 기본 제공하는 캐시
- 같은 트랜잭션 내에서 동일 엔티티 재조회 시 DB 쿼리 없이 반환
- 동일성(identity) 보장: `em.find(id) == em.find(id)`

---

### 2차 캐시 (L2 Cache) - Hibernate 레벨

| 항목 | 설명 |
|------|------|
| **범위** | SessionFactory (애플리케이션) 단위 |
| **생명주기** | 애플리케이션 시작 ~ 종료 |
| **설정** | `@Cacheable`, `@Cache` + 캐시 프로바이더 |
| **공유** | 가능 (여러 트랜잭션에서 공유) |

**2차 캐시 프로바이더 비교:**

| 프로바이더 | 타입 | 속도 | 분산 지원 | 특징 |
|-----------|------|------|----------|------|
| **EhCache** | 로컬 JVM | 나노초 | △ (제한적) | 가장 간단, 단일 서버에 적합 |
| **Hazelcast** | 분산 IMDG | 마이크로초 | ✓ | 임베디드 모드, Near Cache |
| **Infinispan** | 분산 IMDG | 마이크로초 | ✓ | JBoss 환경, JTA 트랜잭션 |
| **Redisson** | 외부 서버 | 밀리초 | ✓ | 영속성, 애플리케이션 독립적 |

**엔티티에 2차 캐시 적용:**
```java
@Entity
@Cacheable                                              // JPA 표준
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)     // Hibernate
public class Member { ... }
```

---

### 애플리케이션 캐시 (Spring Cache)

| 항목 | 설명 |
|------|------|
| **범위** | 메서드 레벨 |
| **생명주기** | 애플리케이션 시작 ~ 종료 (또는 TTL) |
| **설정** | `@Cacheable`, `@CachePut`, `@CacheEvict` |
| **대상** | 메서드 반환값 (엔티티뿐 아니라 DTO, 계산 결과 등) |

**Spring Cache 프로바이더 비교:**

| 프로바이더 | 타입 | 속도 | 분산 지원 | 특징 |
|-----------|------|------|----------|------|
| **Caffeine** | 로컬 JVM | 나노초 | ✗ | 가장 빠름, 단일 서버 |
| **EhCache** | 로컬 JVM | 나노초 | △ | Hibernate L2와 통합 가능 |
| **Hazelcast** | 분산 IMDG | 마이크로초 | ✓ | 클러스터 환경 |
| **Redis** | 외부 서버 | 밀리초 | ✓ | 대규모 분산, 영속성 |
| **Infinispan** | 분산 IMDG | 마이크로초 | ✓ | 클러스터 환경 |

---

### 캐시 선택 가이드

```
단일 서버인가? ─────────────────────────────────────────┐
       │                                               │
      YES                                             NO
       │                                               │
       ▼                                               ▼
  ┌─────────┐                               다중 인스턴스인가?
  │Caffeine │                                     │
  │+ EhCache│                          ┌──────────┴──────────┐
  └─────────┘                         YES                   NO
                                       │                     │
                              캐시 영속성 필요?          외부 서비스와
                                   │                   캐시 공유 필요?
                          ┌────────┴────────┐              │
                         YES               NO             YES
                          │                 │              │
                          ▼                 ▼              ▼
                      ┌───────┐      ┌───────────┐    ┌───────┐
                      │ Redis │      │ Hazelcast │    │ Redis │
                      └───────┘      │ Infinispan│    └───────┘
                                     └───────────┘
```

| 상황 | 추천 |
|------|------|
| 단일 서버, 최고 성능 | Caffeine |
| 단일 서버, Hibernate L2 | EhCache |
| 다중 인스턴스, 간단한 설정 | Hazelcast |
| 다중 인스턴스, JBoss 환경 | Infinispan |
| 대규모 분산, 캐시 영속성 필요 | Redis |
| 다양한 언어/플랫폼 공유 | Redis |

### 2. EntityManager 주요 기능

#### 영속성 컨텍스트 관리
```java
em.persist(entity);   // 영속화
em.find(Class, id);   // 조회
em.merge(entity);     // 병합 (준영속 → 영속)
em.detach(entity);    // 준영속 상태로 전환
em.remove(entity);    // 삭제
em.flush();           // DB 동기화
em.clear();           // 영속성 컨텍스트 초기화
em.refresh(entity);   // DB에서 다시 로드
```

#### 변경 감지 (Dirty Checking)
```java
@Transactional
public void update(Long id) {
    Member member = em.find(Member.class, id);
    member.setName("새 이름");  // 별도 save 불필요, 자동 UPDATE
}
```

### 3. Repository 쿼리 방식

| 방식 | 설명 | 예시 |
|------|------|------|
| 메서드 이름 | 메서드 명으로 쿼리 생성 | `findByEmail(String email)` |
| @Query JPQL | JPQL 직접 작성 | `@Query("SELECT m FROM Member m WHERE...")` |
| @Query Native | 네이티브 SQL | `@Query(value = "...", nativeQuery = true)` |
| Criteria API | 타입 안전 동적 쿼리 | `CriteriaBuilder` 사용 |
| QueryDSL | 타입 안전 + 가독성 | `QMember.member.name.eq(...)` |

### 4. N+1 문제 해결

```java
// Fetch Join
@Query("SELECT m FROM Member m JOIN FETCH m.team")
List<Member> findAllWithTeam();

// EntityGraph
@EntityGraph(attributePaths = {"team", "orders"})
List<Member> findAll();

// Batch Size (application.yml)
hibernate.default_batch_fetch_size: 100
```

### 5. 동시성 제어

#### 낙관적 락 (Optimistic Lock)
```java
@Entity
public class Product {
    @Version
    private Long version;  // 버전 필드
}
```

#### 비관적 락 (Pessimistic Lock)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);
```

### 6. 벌크 연산

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Member m SET m.status = :status WHERE m.id IN :ids")
int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") MemberStatus status);
```

**주의:** 벌크 연산은 영속성 컨텍스트를 우회하므로 `clearAutomatically = true` 필요

## 프로젝트 구조

```
spring-jpa-sample/
├── src/main/java/com/example/jpa/
│   ├── config/
│   │   ├── CacheConfig.java           # Caffeine 로컬 캐시 (default)
│   │   ├── HazelcastCacheConfig.java  # Hazelcast 분산 캐시
│   │   ├── RedisCacheConfig.java      # Redis 캐시
│   │   ├── InfinispanCacheConfig.java # Infinispan 분산 캐시
│   │   └── JpaConfig.java             # JPA 설정
│   ├── entity/
│   │   ├── Member.java            # 2차 캐시 적용 엔티티
│   │   ├── Product.java           # NaturalId + Version
│   │   ├── Order.java
│   │   └── ...
│   ├── repository/
│   │   ├── MemberRepository.java  # 다양한 쿼리 방식
│   │   ├── MemberRepositoryCustom.java
│   │   └── MemberRepositoryImpl.java  # 동적 쿼리
│   ├── service/
│   │   ├── MemberService.java     # Spring Cache 어노테이션
│   │   ├── ProductService.java    # 동시성 제어
│   │   └── EntityManagerExampleService.java  # EM 직접 사용
│   └── controller/
└── src/main/resources/
    ├── application.yml                # 기본 설정 (Ehcache)
    ├── application-hazelcast.yml      # Hazelcast 프로파일
    ├── application-redis.yml          # Redis 프로파일
    ├── application-infinispan.yml     # Infinispan 프로파일
    ├── ehcache.xml                    # EhCache 설정
    ├── infinispan.xml                 # Infinispan 설정
    └── redisson.yaml                  # Redisson 설정
```

## 실행 방법

### 기본 실행 (Caffeine + Ehcache)
```bash
./gradlew bootRun
```

### Hazelcast로 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=hazelcast'
```

### Redis로 실행 (Redis 서버 필요)
```bash
# Redis 서버 시작
docker run -d -p 6379:6379 redis:latest

# 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=redis'
```

### Infinispan으로 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=infinispan'
```

## API 문서

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

---

## 캐싱 테스트 가이드

### 사전 준비

```bash
# PostgreSQL 시작
docker-compose up -d

# 애플리케이션 실행
./gradlew :spring-jpa-sample:bootRun
```

### 1. 1차 캐시 (영속성 컨텍스트) 테스트

1차 캐시는 같은 트랜잭션 내에서 동일 엔티티 재조회 시 DB 쿼리 없이 캐시에서 반환합니다.

**테스트 방법:**
`EntityManagerExampleService.firstLevelCacheExample()` 메서드의 로그 확인

```
# 로그 출력 예시
=== 1차 캐시 예제 ===
첫 번째 조회: 홍길동        ← SQL 실행됨
두 번째 조회: 홍길동        ← SQL 실행 안됨 (1차 캐시)
동일 객체 여부: true
```

**확인 포인트:**
- 첫 번째 `em.find()`: SELECT 쿼리 실행
- 두 번째 `em.find()`: 쿼리 없음 (1차 캐시 적중)

---

### 2. 2차 캐시 (Hibernate L2 Cache) 테스트

2차 캐시는 여러 트랜잭션/세션에서 공유되는 캐시입니다.

**테스트 방법:**

```bash
# 1. 회원 생성
curl -X POST "http://localhost:8080/api/members?name=테스트&email=test@example.com"

# 2. 첫 번째 조회 (DB에서 조회, 캐시에 저장)
curl http://localhost:8080/api/members/1

# 3. 두 번째 조회 (2차 캐시에서 조회)
curl http://localhost:8080/api/members/1
```

**로그 확인:**
```
# 첫 번째 조회 - L2C miss, DB 조회 후 캐시 저장
Session Metrics {
    L2C puts: 1      ← 캐시에 저장
    L2C hits: 0
    L2C misses: 1    ← 캐시 미스
}

# 두 번째 조회 - L2C hit, DB 조회 없음
Session Metrics {
    L2C puts: 0
    L2C hits: 1      ← 캐시 적중!
    L2C misses: 0
}
```

---

### 3. Spring Cache (Caffeine) 테스트

메서드 레벨 캐싱으로, `@Cacheable` 어노테이션이 적용된 메서드 결과를 캐싱합니다.

**테스트 방법:**

```bash
# 1. 회원 생성
curl -X POST "http://localhost:8080/api/members?name=캐시테스트&email=cache@example.com"

# 2. 첫 번째 조회 (DB 조회)
curl http://localhost:8080/api/members/1
# 로그: "DB에서 Member 조회: 1"

# 3. 두 번째 조회 (캐시에서 조회 - 로그 없음)
curl http://localhost:8080/api/members/1
# 로그 없음 = 캐시 적중!

# 4. 회원 수정 (@CachePut으로 캐시 갱신)
curl -X PUT "http://localhost:8080/api/members/1?name=수정됨&status=ACTIVE"

# 5. 다시 조회 (갱신된 캐시에서 조회)
curl http://localhost:8080/api/members/1

# 6. 삭제 (@CacheEvict로 캐시 제거)
curl -X DELETE http://localhost:8080/api/members/1
```

**확인 포인트:**
- `@Cacheable`: "DB에서 Member 조회" 로그가 첫 번째만 출력
- `@CachePut`: 수정 후에도 캐시가 최신 상태 유지
- `@CacheEvict`: 삭제 후 캐시에서 제거됨

---

### 4. Hazelcast 분산 캐시 테스트

```bash
# Hazelcast 프로파일로 실행
./gradlew :spring-jpa-sample:bootRun --args='--spring.profiles.active=hazelcast'
```

**분산 캐시 테스트 (2개 인스턴스):**

```bash
# 터미널 1: 8080 포트
SERVER_PORT=8080 ./gradlew :spring-jpa-sample:bootRun --args='--spring.profiles.active=hazelcast'

# 터미널 2: 8081 포트
SERVER_PORT=8081 ./gradlew :spring-jpa-sample:bootRun --args='--spring.profiles.active=hazelcast --server.port=8081'
```

```bash
# 8080에서 회원 생성
curl -X POST "http://localhost:8080/api/members?name=분산테스트&email=hazel@example.com"

# 8080에서 조회 (캐시 저장)
curl http://localhost:8080/api/members/1

# 8081에서 조회 (분산 캐시에서 조회 - Near Cache)
curl http://localhost:8081/api/members/1
```

**확인 포인트:**
- 두 인스턴스가 클러스터로 연결됨 (로그에 "Members [2]" 표시)
- 한 인스턴스에서 저장한 캐시를 다른 인스턴스에서 조회 가능

---

### 5. Redis 캐시 테스트

```bash
# Redis 서버 시작
docker run -d --name redis -p 6379:6379 redis:latest

# Redis 프로파일로 실행
./gradlew :spring-jpa-sample:bootRun --args='--spring.profiles.active=redis'
```

**테스트:**

```bash
# 회원 생성 및 조회
curl -X POST "http://localhost:8080/api/members?name=Redis테스트&email=redis@example.com"
curl http://localhost:8080/api/members/1

# Redis에서 캐시 확인
docker exec -it redis redis-cli
> KEYS *
> GET "members::1"
```

**확인 포인트:**
- Redis CLI에서 캐시 키/값 직접 확인 가능
- 애플리케이션 재시작 후에도 캐시 유지됨

---

### 6. Infinispan 분산 캐시 테스트

```bash
# Infinispan 프로파일로 실행
./gradlew :spring-jpa-sample:bootRun --args='--spring.profiles.active=infinispan'
```

Hazelcast와 동일한 방식으로 2개 인스턴스로 분산 캐시 테스트 가능

---

### 캐시 동작 확인 체크리스트

| 캐시 종류 | 확인 방법 |
|----------|----------|
| 1차 캐시 | 같은 트랜잭션 내 두 번째 조회 시 SQL 없음 |
| 2차 캐시 | Session Metrics에서 `L2C hits` 증가 |
| Spring Cache | 두 번째 조회 시 Service 로그 없음 |
| Hazelcast | 다른 인스턴스에서 캐시 조회 가능 |
| Redis | `redis-cli`에서 키 확인 가능 |

### 캐시 통계 로그 해석

```
Session Metrics {
    L2C puts: 1      # 2차 캐시에 저장된 횟수
    L2C hits: 5      # 2차 캐시 적중 횟수 (DB 조회 안함)
    L2C misses: 1    # 2차 캐시 미스 횟수 (DB 조회함)
}
```

- **Cache Hit Ratio** = hits / (hits + misses)
- 비율이 높을수록 캐시 효율이 좋음

---

## 참고 자료

- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/orm/6.2/userguide/html_single/Hibernate_User_Guide.html)
- [EhCache Documentation](https://www.ehcache.org/documentation/)
- [Hazelcast Documentation](https://docs.hazelcast.com/)
- [Infinispan Documentation](https://infinispan.org/documentation/)
- [Redis Documentation](https://redis.io/docs/)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
