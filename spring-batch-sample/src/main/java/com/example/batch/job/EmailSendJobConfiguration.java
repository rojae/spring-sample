package com.example.batch.job;

import com.example.batch.entity.Email;
import com.example.batch.job.listener.EmailJobExecutionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmailSendJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EmailJobExecutionListener jobExecutionListener;

    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    /**
     * 기본 이메일 발송 Job
     */
    @Bean
    public Job emailSendJob(Step emailSendStep) {
        return new JobBuilder("emailSendJob", jobRepository)
                .listener(jobExecutionListener)
                .start(emailSendStep)
                .build();
    }

    /**
     * 이메일 발송 Step
     * - Processor에서 예외를 처리하므로 faultTolerant 불필요
     * - 모든 이메일이 Writer로 전달되어 상태 업데이트
     */
    @Bean
    public Step emailSendStep(
            ItemReader<Email> emailItemReader,
            ItemProcessor<Email, Email> emailItemProcessor,
            ItemWriter<Email> emailItemWriter
    ) {
        return new StepBuilder("emailSendStep", jobRepository)
                .<Email, Email>chunk(chunkSize, transactionManager)
                .reader(emailItemReader)
                .processor(emailItemProcessor)
                .writer(emailItemWriter)
                .build();
    }
}
