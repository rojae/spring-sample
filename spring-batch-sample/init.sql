-- 이메일 발송 큐 테이블
CREATE TABLE IF NOT EXISTS email_send_queue (
    id BIGSERIAL PRIMARY KEY,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_email_status ON email_send_queue(status);
CREATE INDEX IF NOT EXISTS idx_email_created_at ON email_send_queue(created_at);

-- 샘플 데이터 삽입 (100건)
INSERT INTO email_send_queue (to_email, subject, content, status)
SELECT
    'user' || generate_series || '@example.com',
    'Welcome Email #' || generate_series,
    'Hello! This is test email content #' || generate_series,
    'PENDING'
FROM generate_series(1, 100);

-- 일부 잘못된 이메일 추가 (테스트용)
INSERT INTO email_send_queue (to_email, subject, content, status) VALUES
('invalid@test.com', 'Invalid Test', 'This will fail validation', 'PENDING'),
('bad-email-format', 'Bad Format', 'This has invalid format', 'PENDING');
