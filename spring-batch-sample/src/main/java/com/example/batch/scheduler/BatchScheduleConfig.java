package com.example.batch.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "batch.scheduling.enabled", havingValue = "true", matchIfMissing = false)
public class BatchScheduleConfig {

    private final JobLauncher jobLauncher;
    private final Job emailSendJob;

    public BatchScheduleConfig(
            JobLauncher jobLauncher,
            @Qualifier("emailSendJob") Job emailSendJob
    ) {
        this.jobLauncher = jobLauncher;
        this.emailSendJob = emailSendJob;
    }

    /**
     * 30초마다 이메일 발송 배치 실행
     */
    @Scheduled(fixedRate = 30000)
    public void runEmailSendJob() {
        try {
            log.info("========== [Scheduled] Starting email send job ==========");

            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(emailSendJob, params);

            log.info("========== [Scheduled] Email send job completed ==========");

        } catch (Exception e) {
            log.error("Scheduled batch job failed", e);
        }
    }
}
