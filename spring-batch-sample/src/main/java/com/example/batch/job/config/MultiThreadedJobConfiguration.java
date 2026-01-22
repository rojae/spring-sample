package com.example.batch.job.config;

import com.example.batch.entity.Email;
import com.example.batch.job.listener.EmailJobExecutionListener;
import com.example.batch.service.EmailSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MultiThreadedJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EmailJobExecutionListener jobExecutionListener;

    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    @Value("${batch.skip-limit:100}")
    private int skipLimit;

    @Value("${batch.thread-count:4}")
    private int threadCount;

    /**
     * 멀티스레드 이메일 발송 Job
     */
    @Bean
    public Job multiThreadedEmailSendJob(
            @Qualifier("multiThreadedEmailSendStep") Step multiThreadedEmailSendStep
    ) {
        return new JobBuilder("multiThreadedEmailSendJob", jobRepository)
                .listener(jobExecutionListener)
                .start(multiThreadedEmailSendStep)
                .build();
    }

    /**
     * 멀티스레드 이메일 발송 Step
     */
    @Bean
    public Step multiThreadedEmailSendStep(
            @Qualifier("threadSafeEmailReader") JpaPagingItemReader<Email> threadSafeEmailReader,
            ItemProcessor<Email, Email> emailItemProcessor,
            ItemWriter<Email> emailItemWriter
    ) {
        return new StepBuilder("multiThreadedEmailSendStep", jobRepository)
                .<Email, Email>chunk(chunkSize, transactionManager)
                .reader(threadSafeEmailReader)
                .processor(emailItemProcessor)
                .writer(emailItemWriter)
                .taskExecutor(batchTaskExecutor())
                .faultTolerant()
                .retry(EmailSendException.class)
                .retryLimit(3)
                .skip(EmailSendException.class)
                .skip(IllegalArgumentException.class)
                .skipLimit(skipLimit)
                .build();
    }

    /**
     * 배치용 TaskExecutor
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("batch-thread-");
        executor.initialize();
        return executor;
    }
}
