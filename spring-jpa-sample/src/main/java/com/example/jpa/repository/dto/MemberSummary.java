package com.example.jpa.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO Projection 용 클래스
 * - 필요한 컬럼만 조회하여 성능 최적화
 */
@Getter
@AllArgsConstructor
public class MemberSummary {
    private Long id;
    private String name;
    private String email;
}
