package com.example.batch.job.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Job 실행 리스너
 */
@Slf4j
@Component
public class EmailJobExecutionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("========================================");
        log.info("Email Send Job Started");
        log.info("Job Name: {}", jobExecution.getJobInstance().getJobName());
        log.info("Start Time: {}", jobExecution.getStartTime());
        log.info("========================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();

        long durationSeconds = 0;
        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            durationSeconds = duration.getSeconds();
        }

        log.info("========================================");
        log.info("Email Send Job Finished");
        log.info("Status: {}", jobExecution.getStatus());
        log.info("Exit Status: {}", jobExecution.getExitStatus());
        log.info("Duration: {} seconds", durationSeconds);

        jobExecution.getStepExecutions().forEach(stepExecution -> {
            log.info("Step: {}", stepExecution.getStepName());
            log.info("  - Read Count: {}", stepExecution.getReadCount());
            log.info("  - Write Count: {}", stepExecution.getWriteCount());
            log.info("  - Skip Count: {}", stepExecution.getSkipCount());
            log.info("  - Commit Count: {}", stepExecution.getCommitCount());
            log.info("  - Rollback Count: {}", stepExecution.getRollbackCount());
        });
        log.info("========================================");
    }
}
