# Spring Batch Sample Project

Spring Batch를 활용한 이메일 발송 배치 샘플 프로젝트입니다.

## 기술 스택

- Java 21
- Spring Boot 3.2.2
- Spring Batch 5.x
- PostgreSQL (Docker)
- Gradle

## 프로젝트 구조

```
spring-batch-sample/
├── docker-compose.yml              # PostgreSQL Docker
├── build.gradle
├── src/main/java/com/example/batch/
│   ├── BatchApplication.java       # Main Application
│   ├── entity/                     # JPA Entity
│   │   ├── Email.java
│   │   └── EmailStatus.java
│   ├── repository/                 # JPA Repository
│   │   └── EmailRepository.java
│   ├── service/                    # 비즈니스 로직
│   │   ├── EmailService.java
│   │   └── EmailSendException.java
│   ├── job/                        # Batch Job 설정
│   │   ├── EmailSendJobConfiguration.java
│   │   ├── EmailItemReaderConfig.java
│   │   ├── EmailItemProcessor.java
│   │   ├── EmailItemWriter.java
│   │   ├── config/
│   │   │   ├── MultiThreadedJobConfiguration.java
│   │   │   └── PartitionedJobConfiguration.java
│   │   ├── partitioner/
│   │   │   └── EmailIdRangePartitioner.java
│   │   └── listener/
│   │       └── EmailJobExecutionListener.java
│   ├── scheduler/                  # 스케줄링
│   │   └── BatchScheduleConfig.java
│   └── controller/                 # REST API
│       └── BatchController.java
├── Dockerfile
└── k8s/
    └── cronjob.yaml
```

## 실행 방법

### 1. PostgreSQL 실행

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 IDE에서 `BatchApplication.java` 실행

### 3. 배치 실행

```bash
# 기본 배치
curl -X POST http://localhost:8080/api/batch/email

# 멀티스레드 배치
curl -X POST http://localhost:8080/api/batch/email/multi-threaded

# 파티션 배치
curl -X POST http://localhost:8080/api/batch/email/partitioned
```

## 주요 기능

### 1. 기본 배치 (emailSendJob)
- Chunk 기반 처리 (기본 1000건)
- Skip/Retry 전략 적용
- 트랜잭션 관리

### 2. 멀티스레드 배치 (multiThreadedEmailSendJob)
- 여러 스레드가 동시에 Chunk 처리
- Thread-safe Reader 사용

### 3. 파티션 배치 (partitionedEmailSendJob)
- 데이터를 ID 범위로 분할
- 각 파티션을 병렬 처리

## 설정

### application.yml 주요 설정

```yaml
batch:
  chunk-size: 1000    # Chunk 크기
  page-size: 1000     # Reader 페이지 크기
  skip-limit: 100     # Skip 제한
  thread-count: 4     # 스레드 수
```

### 환경별 프로필

- `local`: 개발 환경 (chunk-size: 100, thread-count: 2)
- `prod`: 운영 환경 (chunk-size: 1000, thread-count: 8)

## API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/batch/email | 기본 배치 실행 |
| POST | /api/batch/email/multi-threaded | 멀티스레드 배치 실행 |
| POST | /api/batch/email/partitioned | 파티션 배치 실행 |

## Docker 빌드 및 실행

```bash
# JAR 빌드
./gradlew build

# Docker 이미지 빌드
docker build -t email-batch:latest .

# Docker 실행
docker run -e SPRING_PROFILES_ACTIVE=prod \
           -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/batch_db \
           email-batch:latest
```

## Kubernetes CronJob

```bash
# Secret 생성
kubectl apply -f k8s/cronjob.yaml

# CronJob 확인
kubectl get cronjob email-send-batch
```

## 테스트

```bash
./gradlew test
```
