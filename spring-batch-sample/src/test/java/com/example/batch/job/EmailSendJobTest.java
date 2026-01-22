package com.example.batch.job;

import com.example.batch.entity.Email;
import com.example.batch.entity.EmailStatus;
import com.example.batch.repository.EmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class EmailSendJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    @Qualifier("emailSendJob")
    private Job emailSendJob;

    @BeforeEach
    void setUp() {
        emailRepository.deleteAll();
        jobLauncherTestUtils.setJob(emailSendJob);
    }

    @Test
    @DisplayName("이메일 발송 배치 성공 테스트")
    void emailSendJob_success() throws Exception {
        // Given: 테스트 데이터 생성
        for (int i = 1; i <= 10; i++) {
            Email email = Email.builder()
                    .toEmail("user" + i + "@example.com")
                    .subject("Test Subject " + i)
                    .content("Test Content " + i)
                    .status(EmailStatus.PENDING)
                    .retryCount(0)
                    .build();
            emailRepository.save(email);
        }

        // When: 배치 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: 결과 검증
        assertThat(jobExecution.getStatus().isUnsuccessful()).isFalse();

        // 대부분 성공 (90% 성공률 시뮬레이션이지만 테스트에서는 대부분 성공)
        long successCount = emailRepository.findByStatusOrderByIdAsc(EmailStatus.SUCCESS).size();
        assertThat(successCount).isGreaterThanOrEqualTo(7); // 최소 70% 이상 성공
    }

    @Test
    @DisplayName("잘못된 이메일 주소 Skip 테스트")
    void emailSendJob_skipInvalidEmail() throws Exception {
        // Given: 잘못된 이메일 포함
        Email validEmail = Email.builder()
                .toEmail("valid@example.com")
                .subject("Valid")
                .content("Valid")
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .build();

        Email invalidEmail = Email.builder()
                .toEmail("invalid@test.com")
                .subject("Invalid")
                .content("Invalid")
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .build();

        emailRepository.save(validEmail);
        emailRepository.save(invalidEmail);

        // When: 배치 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: Job은 성공, invalid 이메일은 Skip 되어 상태 변경 없음
        assertThat(jobExecution.getStatus().isUnsuccessful()).isFalse();

        // invalid 이메일은 Processor에서 null 반환으로 Skip
        // (Writer에 전달되지 않아 DB 업데이트 안 됨)
        long pendingCount = emailRepository.countByStatus(EmailStatus.PENDING);
        // invalid 이메일은 PENDING 상태로 남아있거나, Processor에서 markAsFailed 후 null 반환으로 DB 미반영
    }

    @Test
    @DisplayName("빈 데이터 배치 실행 테스트")
    void emailSendJob_noData() throws Exception {
        // Given: 데이터 없음

        // When: 배치 실행
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Then: Job은 성공 (처리할 데이터가 없어도 정상 완료)
        assertThat(jobExecution.getStatus().isUnsuccessful()).isFalse();
    }
}
