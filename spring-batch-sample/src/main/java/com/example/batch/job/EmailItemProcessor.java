package com.example.batch.job;

import com.example.batch.entity.Email;
import com.example.batch.service.EmailSendException;
import com.example.batch.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 이메일 발송 Processor
 * - 성공: status = SUCCESS
 * - 실패: status = FAILED (예외를 던지지 않고 상태만 변경하여 Writer로 전달)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailItemProcessor implements ItemProcessor<Email, Email> {

    private final EmailService emailService;

    @Override
    public Email process(Email email) throws Exception {
        log.debug("Processing email: id={}, to={}", email.getId(), email.getToEmail());

        try {
            // 1. 이메일 발송 시도
            emailService.send(
                    email.getToEmail(),
                    email.getSubject(),
                    email.getContent()
            );

            // 2. 성공 처리
            email.markAsSent();
            log.info("Email sent successfully: id={}, to={}",
                    email.getId(), email.getToEmail());

        } catch (IllegalArgumentException e) {
            // 3. 검증 실패 - FAILED로 마킹 후 반환 (Writer로 전달)
            log.warn("Invalid email address: id={}, to={}, error={}",
                    email.getId(), email.getToEmail(), e.getMessage());
            email.markAsFailed("Invalid email: " + e.getMessage());

        } catch (EmailSendException e) {
            // 4. 발송 실패 - FAILED로 마킹 후 반환 (Writer로 전달)
            log.error("Failed to send email: id={}, to={}, error={}",
                    email.getId(), email.getToEmail(), e.getMessage());
            email.markAsFailed(e.getMessage());
        }

        // 항상 email 반환 → Writer에서 상태 업데이트
        return email;
    }
}
