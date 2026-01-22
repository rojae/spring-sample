package com.example.batch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 이메일 발송 서비스 (시뮬레이션)
 */
@Slf4j
@Service
public class EmailService {

    private static final double SUCCESS_RATE = 0.9; // 90% 성공률

    /**
     * 이메일 발송
     *
     * @return true: 성공, false: 실패
     * @throws IllegalArgumentException 잘못된 이메일 주소
     * @throws EmailSendException 발송 실패
     */
    public boolean send(String toEmail, String subject, String content) {
        log.debug("Sending email to: {}, subject: {}", toEmail, subject);

        // 1. 이메일 주소 검증
        validateEmail(toEmail);

        // 2. 네트워크 지연 시뮬레이션 (10~50ms)
        simulateNetworkDelay();

        // 3. 랜덤 실패 시뮬레이션 (10% 확률)
        if (Math.random() > SUCCESS_RATE) {
            log.warn("Failed to send email to: {} (simulated failure)", toEmail);
            throw new EmailSendException("SMTP connection timeout");
        }

        log.debug("Email sent successfully to: {}", toEmail);
        return true;
    }

    /**
     * 이메일 주소 검증
     */
    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be empty");
        }

        // "invalid"가 포함된 이메일은 무조건 실패
        if (email.contains("invalid")) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }

        // 기본 형식 검증
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }
    }

    /**
     * 네트워크 지연 시뮬레이션
     */
    private void simulateNetworkDelay() {
        try {
            long delay = 10 + (long) (Math.random() * 40); // 10~50ms
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Email sending interrupted", e);
        }
    }
}
