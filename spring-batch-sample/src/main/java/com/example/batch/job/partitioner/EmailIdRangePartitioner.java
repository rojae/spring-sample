package com.example.batch.job.partitioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 이메일 ID 범위 기반 Partitioner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailIdRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // 1. 전체 데이터 범위 조회
        Long minId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM email_send_queue WHERE status = 'PENDING'",
                Long.class
        );
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM email_send_queue WHERE status = 'PENDING'",
                Long.class
        );

        if (minId == null || maxId == null) {
            log.info("No PENDING emails found. Skipping partitioning.");
            return new HashMap<>();
        }

        log.info("Partitioning email IDs from {} to {} into {} partitions", minId, maxId, gridSize);

        // 2. 파티션 크기 계산
        long targetSize = (maxId - minId) / gridSize + 1;

        // 3. 파티션별 ExecutionContext 생성
        Map<String, ExecutionContext> result = new HashMap<>();

        long start = minId;
        long end = start + targetSize - 1;

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", start);
            context.putLong("maxId", Math.min(end, maxId));
            context.putString("partitionName", "partition" + i);

            result.put("partition" + i, context);

            log.debug("Created partition{}: minId={}, maxId={}", i, start, Math.min(end, maxId));

            start = end + 1;
            end = start + targetSize - 1;
        }

        return result;
    }
}
