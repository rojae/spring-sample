package com.example.batch.controller;

import com.example.batch.entity.Email;
import com.example.batch.entity.EmailStatus;
import com.example.batch.repository.EmailRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Tag(name = "Batch API", description = "이메일 배치 관련 API")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final EmailRepository emailRepository;

    @Qualifier("emailSendJob")
    private final Job emailSendJob;

    @Qualifier("multiThreadedEmailSendJob")
    private final Job multiThreadedEmailSendJob;

    @Qualifier("partitionedEmailSendJob")
    private final Job partitionedEmailSendJob;

    // ========== 배치 실행 API ==========

    @Operation(summary = "기본 이메일 배치 실행", description = "단일 스레드로 이메일 발송 배치를 실행합니다")
    @PostMapping("/email")
    public Map<String, Object> runEmailBatch() {
        return runJob(emailSendJob, "emailSendJob");
    }

    @Operation(summary = "멀티스레드 배치 실행", description = "여러 스레드로 이메일 발송 배치를 실행합니다")
    @PostMapping("/email/multi-threaded")
    public Map<String, Object> runMultiThreadedEmailBatch() {
        return runJob(multiThreadedEmailSendJob, "multiThreadedEmailSendJob");
    }

    @Operation(summary = "파티션 배치 실행", description = "데이터를 파티션으로 나누어 병렬 처리합니다")
    @PostMapping("/email/partitioned")
    public Map<String, Object> runPartitionedEmailBatch() {
        return runJob(partitionedEmailSendJob, "partitionedEmailSendJob");
    }

    // ========== 테스트 데이터 API ==========

    @Operation(summary = "테스트 이메일 추가", description = "PENDING 상태의 테스트 이메일을 추가합니다")
    @PostMapping("/test/email")
    public Map<String, Object> addTestEmail(
            @RequestParam(defaultValue = "test@example.com") String toEmail,
            @RequestParam(defaultValue = "Test Subject") String subject,
            @RequestParam(defaultValue = "Test Content") String content
    ) {
        Email email = Email.builder()
                .toEmail(toEmail)
                .subject(subject)
                .content(content)
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .build();

        Email saved = emailRepository.save(email);

        return Map.of(
                "status", "SUCCESS",
                "message", "테스트 이메일 추가 완료",
                "email", Map.of(
                        "id", saved.getId(),
                        "toEmail", saved.getToEmail(),
                        "status", saved.getStatus().name()
                )
        );
    }

    @Operation(summary = "대량 테스트 이메일 추가", description = "지정한 개수만큼 테스트 이메일을 추가합니다")
    @PostMapping("/test/email/bulk")
    public Map<String, Object> addBulkTestEmails(
            @RequestParam(defaultValue = "10") int count
    ) {
        for (int i = 1; i <= count; i++) {
            Email email = Email.builder()
                    .toEmail("user" + System.currentTimeMillis() + "_" + i + "@example.com")
                    .subject("Bulk Test Subject " + i)
                    .content("Bulk Test Content " + i)
                    .status(EmailStatus.PENDING)
                    .retryCount(0)
                    .build();
            emailRepository.save(email);
        }

        return Map.of(
                "status", "SUCCESS",
                "message", count + "개의 테스트 이메일 추가 완료"
        );
    }

    // ========== 조회 API ==========

    @Operation(summary = "이메일 상태별 통계", description = "PENDING, SUCCESS, FAILED 상태별 개수를 조회합니다")
    @GetMapping("/email/stats")
    public Map<String, Object> getEmailStats() {
        long pending = emailRepository.countByStatus(EmailStatus.PENDING);
        long success = emailRepository.countByStatus(EmailStatus.SUCCESS);
        long failed = emailRepository.countByStatus(EmailStatus.FAILED);

        return Map.of(
                "pending", pending,
                "success", success,
                "failed", failed,
                "total", pending + success + failed
        );
    }

    @Operation(summary = "상태별 이메일 조회", description = "지정한 상태의 이메일 목록을 조회합니다")
    @GetMapping("/email/list")
    public List<Email> getEmailsByStatus(
            @RequestParam(defaultValue = "PENDING") EmailStatus status
    ) {
        return emailRepository.findByStatusOrderByIdAsc(status);
    }

    @Operation(summary = "모든 이메일 PENDING으로 리셋", description = "테스트를 위해 모든 이메일을 PENDING 상태로 초기화합니다")
    @PostMapping("/test/reset")
    public Map<String, Object> resetAllEmails() {
        List<Email> allEmails = emailRepository.findAll();
        for (Email email : allEmails) {
            email.setStatus(EmailStatus.PENDING);
            email.setSentAt(null);
            email.setErrorMessage(null);
            email.setRetryCount(0);
        }
        emailRepository.saveAll(allEmails);

        return Map.of(
                "status", "SUCCESS",
                "message", allEmails.size() + "개의 이메일을 PENDING으로 리셋"
        );
    }

    // ========== Private Methods ==========

    private Map<String, Object> runJob(Job job, String jobName) {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(job, jobParameters);

            return Map.of(
                    "status", "SUCCESS",
                    "message", jobName + " 실행 완료!"
            );

        } catch (Exception e) {
            log.error("Batch execution failed: {}", jobName, e);
            return Map.of(
                    "status", "FAILED",
                    "message", "배치 실행 실패: " + e.getMessage()
            );
        }
    }
}
