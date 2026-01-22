package com.example.batch.job;

import com.example.batch.entity.Email;
import com.example.batch.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * 이메일 발송 결과 저장 Writer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailItemWriter implements ItemWriter<Email> {

    private final EmailRepository emailRepository;

    @Override
    public void write(Chunk<? extends Email> chunk) throws Exception {
        long startTime = System.currentTimeMillis();

        // JPA Repository를 통해 저장 (Batch Update)
        emailRepository.saveAll(chunk.getItems());

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Updated {} emails in {}ms", chunk.size(), elapsed);
    }
}
