package com.example.batch.job.config;

import com.example.batch.entity.Email;
import com.example.batch.job.listener.EmailJobExecutionListener;
import com.example.batch.job.partitioner.EmailIdRangePartitioner;
import com.example.batch.service.EmailSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
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
public class PartitionedJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EmailJobExecutionListener jobExecutionListener;
    private final EmailIdRangePartitioner partitioner;

    @Value("${batch.chunk-size:1000}")
    private int chunkSize;

    @Value("${batch.skip-limit:100}")
    private int skipLimit;

    @Value("${batch.thread-count:4}")
    private int threadCount;

    /**
     * 파티션 이메일 발송 Job
     */
    @Bean
    public Job partitionedEmailSendJob(
            @Qualifier("masterStep") Step masterStep
    ) {
        return new JobBuilder("partitionedEmailSendJob", jobRepository)
                .listener(jobExecutionListener)
                .start(masterStep)
                .build();
    }

    /**
     * Master Step - 파티션 분배
     */
    @Bean
    public Step masterStep(
            @Qualifier("workerStep") Step workerStep
    ) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(threadCount)
                .taskExecutor(partitionTaskExecutor())
                .build();
    }

    /**
     * Worker Step - 실제 처리
     */
    @Bean
    public Step workerStep(
            @Qualifier("partitionedEmailReader") JpaPagingItemReader<Email> partitionedEmailReader,
            ItemProcessor<Email, Email> emailItemProcessor,
            ItemWriter<Email> emailItemWriter
    ) {
        return new StepBuilder("workerStep", jobRepository)
                .<Email, Email>chunk(chunkSize, transactionManager)
                .reader(partitionedEmailReader)
                .processor(emailItemProcessor)
                .writer(emailItemWriter)
                .faultTolerant()
                .retry(EmailSendException.class)
                .retryLimit(3)
                .skip(EmailSendException.class)
                .skip(IllegalArgumentException.class)
                .skipLimit(skipLimit)
                .build();
    }

    /**
     * 파티션용 TaskExecutor
     */
    @Bean
    public TaskExecutor partitionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("partition-");
        executor.initialize();
        return executor;
    }
}
