package com.example.batch.job;

import com.example.batch.entity.Email;
import com.example.batch.entity.EmailStatus;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmailItemReaderConfig {

    private final EntityManagerFactory entityManagerFactory;

    @Value("${batch.page-size:1000}")
    private int pageSize;

    /**
     * 기본 이메일 Reader
     * pageSize: 한 번에 읽어올 데이터 개수
     */
    @Bean
    public JpaPagingItemReader<Email> emailItemReader() {
        return new JpaPagingItemReaderBuilder<Email>()
                .name("emailItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT e FROM Email e WHERE e.status = :status ORDER BY e.id ASC")
                .parameterValues(Map.of("status", EmailStatus.PENDING))
                .pageSize(pageSize)
                .build();
    }

    /**
     * Thread-safe 이메일 Reader (멀티스레드용)
     * saveState(false): 상태 저장 비활성화
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<Email> threadSafeEmailReader() {
        return new JpaPagingItemReaderBuilder<Email>()
                .name("threadSafeEmailReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT e FROM Email e WHERE e.status = :status ORDER BY e.id ASC")
                .parameterValues(Map.of("status", EmailStatus.PENDING))
                .pageSize(pageSize)
                .saveState(false)
                .build();
    }

    /**
     * 파티션용 이메일 Reader
     * StepScope로 파티션 파라미터 주입
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<Email> partitionedEmailReader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId
    ) {
        log.info("Creating partitioned reader for range: {} - {}", minId, maxId);

        return new JpaPagingItemReaderBuilder<Email>()
                .name("partitionedEmailReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                    SELECT e FROM Email e
                    WHERE e.status = :status
                    AND e.id BETWEEN :minId AND :maxId
                    ORDER BY e.id ASC
                """)
                .parameterValues(Map.of(
                        "status", EmailStatus.PENDING,
                        "minId", minId,
                        "maxId", maxId
                ))
                .pageSize(pageSize)
                .build();
    }
}
